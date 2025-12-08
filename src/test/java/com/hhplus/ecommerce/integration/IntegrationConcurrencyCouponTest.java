package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coupon 동시성 통합 테스트
 *
 * 테스트 범위:
 * - 선착순 쿠폰 발급 (100명 동시 요청, 10개 한정)
 * - DB 레벨 비관적 락 (SELECT FOR UPDATE) 동작 확인
 * - 초과 발급 방지 검증
 *
 * 개선 효과 검증:
 * - 3중 Lock → 1중 Lock으로 개선
 * - Lock 보유 시간 감소
 * - TPS 향상 확인
 */
@DisplayName("[Integration] Coupon 동시성 테스트")
class IntegrationConcurrencyCouponTest extends BaseIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private UserRepository userRepository;

    private Coupon testCoupon;

    @BeforeEach
    @Transactional
    void setUp() {
        // 테스트용 쿠폰 생성 (10개 한정)
        testCoupon = Coupon.builder()
                .couponName("선착순 쿠폰")
                .description("동시성 테스트용")
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .discountRate(BigDecimal.ZERO)
                .totalQuantity(10)
                .remainingQty(10)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(1))
                .isActive(true)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        couponRepository.save(testCoupon);

        // 테스트용 사용자 100명 생성
        for (int i = 1; i <= 100; i++) {
            User user = User.createUser(
                    "test" + i + "@example.com",
                    "hash",
                    "테스트" + i,
                    "010-0000-" + String.format("%04d", i)
            );
            userRepository.save(user);
        }
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 - 100명 요청, 10개 한정")
    void testConcurrentIssueCoupon_FirstComeFirstServe() throws InterruptedException {
        // Given
        int totalUsers = 100;
        int availableCoupons = 10;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // When: 100명이 동시에 쿠폰 발급 시도
        for (long userId = 1; userId <= totalUsers; userId++) {
            final long currentUserId = userId;
            executor.submit(() -> {
                try {
                    couponService.issueCoupon(currentUserId, testCoupon.getCouponId());
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    // 쿠폰 소진 또는 중복 발급
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        long elapsedTime = System.currentTimeMillis() - startTime;
        executor.shutdown();

        // Then
        assertEquals(availableCoupons, successCount.get(),
                "정확히 10개만 발급되어야 함");
        assertEquals(totalUsers - availableCoupons, failureCount.get(),
                "90개 요청은 실패해야 함");

        // 최종 쿠폰 재고 확인
        Coupon finalCoupon = couponRepository.findById(testCoupon.getCouponId())
                .orElseThrow();
        assertEquals(0, finalCoupon.getRemainingQty(), "쿠폰 재고가 0이어야 함");
        assertFalse(finalCoupon.getIsActive(), "쿠폰이 자동으로 비활성화되어야 함");

        System.out.println("성공: " + successCount.get() + ", 실패: " + failureCount.get());
        System.out.println("소요 시간: " + elapsedTime + "ms");
        assertTrue(elapsedTime < 10000, "10초 내에 완료되어야 함 (실제: " + elapsedTime + "ms)");
    }

    @Test
    @DisplayName("순차 쿠폰 발급 - 모두 성공")
    @Transactional
    void testSequentialIssueCoupon_AllSuccess() {
        // Given
        int issueCount = 10;

        // When: 순차적으로 쿠폰 발급
        for (long userId = 1; userId <= issueCount; userId++) {
            couponService.issueCoupon(userId, testCoupon.getCouponId());
        }

        // Then
        Coupon finalCoupon = couponRepository.findById(testCoupon.getCouponId())
                .orElseThrow();
        assertEquals(0, finalCoupon.getRemainingQty(), "순차 처리 시 모두 성공해야 함");
        assertFalse(finalCoupon.getIsActive(), "쿠폰이 자동으로 비활성화되어야 함");
    }

    @Test
    @DisplayName("쿠폰 중복 발급 방지 - UNIQUE 제약")
    void testDuplicateIssuePrevention() {
        // Given
        long userId = 1L;
        couponService.issueCoupon(userId, testCoupon.getCouponId());

        // When & Then: 동일 사용자가 같은 쿠폰 재발급 시도
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> couponService.issueCoupon(userId, testCoupon.getCouponId()),
                "중복 발급 시 예외 발생"
        );

        assertTrue(exception.getMessage().contains("이미 발급받으셨습니다"));
    }
}
