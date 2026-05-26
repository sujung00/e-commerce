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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
 * ⚠️ NOT_SUPPORTED:
 *   BaseIntegrationTest의 @Transactional(REQUIRED)을 오버라이드한다.
 *   이유: 동시성 테스트에서 spawned thread들은 main thread의 Spring 트랜잭션
 *   컨텍스트(ThreadLocal)를 공유하지 않는다. 따라서 @BeforeEach에서 생성한
 *   데이터가 outer test transaction 안에 있으면 spawned thread는 해당 데이터를
 *   볼 수 없다 (MySQL InnoDB REPEATABLE READ). NOT_SUPPORTED를 적용하면
 *   @BeforeEach의 각 save()가 독립 트랜잭션으로 커밋되어 spawned thread에서도
 *   해당 데이터를 조회할 수 있다.
 *
 *   순차 테스트는 같은 thread에서 실행되므로 NOT_SUPPORTED에서도 정상 동작한다.
 */
@DisplayName("[Integration] Coupon 동시성 테스트")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
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
    private List<Long> testUserIds;

    @BeforeEach
    void setUp() {
        // 각 테스트마다 고유한 suffix 사용 (이메일 UNIQUE constraint 충돌 방지)
        // NOT_SUPPORTED이므로 각 save()가 독립 트랜잭션으로 즉시 커밋됨
        long suffix = System.nanoTime();
        testUserIds = new ArrayList<>();

        // 테스트용 쿠폰 생성 (10개 한정)
        testCoupon = Coupon.builder()
                .couponName("선착순쿠폰_" + suffix)
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .totalQuantity(10)
                .remainingQty(10)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(1))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        couponRepository.save(testCoupon);

        // 테스트용 사용자 100명 생성 (커밋됨)
        for (int i = 1; i <= 100; i++) {
            User user = User.builder()
                    .name("테스트" + i + "_" + suffix)
                    .email("concurrent-coupon-" + suffix + "-" + i + "@example.com")
                    .balance(100000L)
                    .build();
            userRepository.save(user);
            testUserIds.add(user.getUserId());
        }
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 - 100명 요청, 10개 한정")
    void testConcurrentIssueCoupon_FirstComeFirstServe() throws InterruptedException {
        // Given
        int totalUsers = 100;
        int availableCoupons = 10;
        long couponId = testCoupon.getCouponId();

        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // When: 100명이 동시에 쿠폰 발급 시도
        for (Long userId : testUserIds) {
            executor.submit(() -> {
                try {
                    couponService.issueCoupon(userId, couponId);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    // 쿠폰 소진 또는 중복 발급 → 예상되는 실패
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
                "정확히 10개만 발급되어야 함 (실제 성공: " + successCount.get() + ")");
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
    void testSequentialIssueCoupon_AllSuccess() {
        // Given: 10개 쿠폰, 10명 순차 발급
        int issueCount = 10;

        // When: 순차적으로 쿠폰 발급
        for (int i = 0; i < issueCount; i++) {
            couponService.issueCoupon(testUserIds.get(i), testCoupon.getCouponId());
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
        long userId = testUserIds.get(0);
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
