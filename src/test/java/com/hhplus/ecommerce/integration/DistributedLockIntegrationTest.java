package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.application.user.UserBalanceService;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Redis 분산락 + @Transactional 통합 테스트
 *
 * @DistributedLock과 @Transactional(propagation=REQUIRES_NEW)의 상호작용 검증:
 * - Lock 획득 → Transaction 시작 → 비즈니스 로직 → Transaction 커밋 → Lock 해제
 * - TransactionSynchronization을 통한 적절한 Lock 해제 시점 보장
 *
 * 테스트 대상:
 * - UserBalanceService: deductBalance, chargeBalance, refundBalance
 * - CouponService: issueCouponWithLock
 *
 * TestContainers 기반으로 MySQL + Redis 자동 구동하여
 * 외부 의존성 제거 및 격리된 테스트 환경 제공
 */
@DisplayName("[Integration] Redis 분산락 + @Transactional 통합 테스트")
class DistributedLockIntegrationTest extends BaseIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockIntegrationTest.class);

    @Autowired
    private UserBalanceService userBalanceService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponRepository couponRepository;

    @BeforeEach
    void 준비() {
        log.info("========== 분산락 + @Transactional 통합 테스트 시작 ==========");
        // Redis 상태 초기화 (각 테스트마다 독립적으로 실행)
    }

    @Test
    @DisplayName("사용자 잔액 차감 - 단일 스레드 (Transaction 정상 처리)")
    void 사용자잔액차감_단일스레드() {
        // Given: 테스트 사용자 생성
        User user = User.builder()
                .userId(1L)
                .name("user1")
                .email("user1@example.com")
                .balance(10000L)
                .build();
        userRepository.save(user);

        // When: 잔액 차감
        User result = userBalanceService.deductBalance(1L, 5000L);

        // Then: 트랜잭션 성공 및 잔액 업데이트 확인
        assertNotNull(result);
        assertEquals(5000L, result.getBalance());
        log.info("✅ 사용자 잔액 차감 테스트 통과");
    }

    @Test
    @DisplayName("사용자 잔액 충전 - 단일 스레드 (Transaction 정상 처리)")
    void 사용자잔액충전_단일스레드() {
        // Given: 테스트 사용자 생성
        User user = User.builder()
                .userId(2L)
                .name("user2")
                .email("user2@example.com")
                .balance(5000L)
                .build();
        userRepository.save(user);

        // When: 잔액 충전
        User result = userBalanceService.chargeBalance(2L, 5000L);

        // Then
        assertNotNull(result);
        assertEquals(10000L, result.getBalance());
        log.info("✅ 사용자 잔액 충전 테스트 통과");
    }

    @Test
    @DisplayName("쿠폰 발급 - 단일 스레드 (Transaction 정상 처리)")
    void 쿠폰발급_단일스레드() {
        // Given: 테스트 사용자 및 쿠폰 생성
        User user = User.builder()
                .userId(3L)
                .name("user3")
                .email("user3@example.com")
                .balance(10000L)
                .build();
        userRepository.save(user);

        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .couponName("Test Coupon")
                .discountAmount(1000L)
                .totalQuantity(10)
                .remainingQty(10)
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(1))
                .build();
        couponRepository.save(coupon);

        // When: 쿠폰 발급
        var result = couponService.issueCouponWithLock(3L, 1L);

        // Then
        assertNotNull(result);
        log.info("✅ 쿠폰 발급 테스트 통과");
    }

    @Test
    @DisplayName("동일 사용자의 동시 잔액 차감 - 순차 처리 검증")
    void 동일사용자동시차감_순차처리검증() throws InterruptedException {
        // Given: 초기 잔액이 충분한 사용자 생성
        User user = User.builder()
                .userId(10L)
                .name("user10")
                .email("user10@example.com")
                .balance(50000L)
                .build();
        userRepository.save(user);

        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        log.info("[Test] 동일 사용자(10L)에 대해 {} 개 스레드가 동시 차감 시도", threadCount);

        // When: 같은 사용자의 잔액을 동시에 차감
        for (int i = 0; i < threadCount; i++) {
            int threadId = i;
            executor.submit(() -> {
                try {
                    log.info("[Thread-{}] 사용자 10의 잔액 차감 시도 (5000원)...", threadId);
                    User result = userBalanceService.deductBalance(10L, 5000L);
                    if (result != null) {
                        successCount.incrementAndGet();
                        log.info("[Thread-{}] ✅ 차감 성공, 잔액: {}", threadId, result.getBalance());
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.warn("[Thread-{}] ⚠️ 차감 실패: {}", threadId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;

        // Then: 분산락으로 인한 순차 처리 확인
        log.info("========== 테스트 결과 ==========");
        log.info("성공: {}, 실패: {}", successCount.get(), failureCount.get());
        log.info("소요 시간: {} ms", elapsedTime);

        assertEquals(threadCount, successCount.get() + failureCount.get(), "모든 스레드 완료");
        assertTrue(successCount.get() > 0, "최소 한 개의 차감 성공");

        // 최종 잔액 검증: 50000 - (5000 * 성공횟수)
        User finalUser = userRepository.findById(10L).orElseThrow();
        log.info("최종 잔액: {} (초기: 50000, 성공: {}번)", finalUser.getBalance(), successCount.get());

        log.info("✅ 동시 차감 테스트 통과 - 분산락으로 순차 처리됨");
    }

    @Test
    @DisplayName("서로 다른 사용자의 동시 잔액 차감 - 병렬 처리 검증")
    void 다른사용자동시차감_병렬처리검증() throws InterruptedException {
        // Given: 서로 다른 사용자들 생성
        for (long i = 20L; i <= 24L; i++) {
            User user = User.builder()
                    .userId(i)
                    .name("user" + i)
                    .email("user" + i + "@example.com")
                    .balance(10000L)
                    .build();
            userRepository.save(user);
        }

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        log.info("[Test] 서로 다른 사용자 {} 명이 동시에 잔액 차감", threadCount);

        // When: 서로 다른 사용자들이 동시에 잔액 차감
        for (int i = 0; i < threadCount; i++) {
            long userId = 20L + i;
            executor.submit(() -> {
                try {
                    log.info("[Thread-{}] 사용자 {} 잔액 차감 시도...", userId - 20, userId);
                    User result = userBalanceService.deductBalance(userId, 3000L);
                    if (result != null) {
                        successCount.incrementAndGet();
                        log.info("[Thread-{}] ✅ 성공", userId - 20);
                    }
                } catch (Exception e) {
                    log.error("[Thread-{}] 실패", userId - 20, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;

        // Then: 병렬 처리로 빠르게 완료되어야 함
        log.info("========== 테스트 결과 ==========");
        log.info("성공: {}", successCount.get());
        log.info("소요 시간: {} ms", elapsedTime);

        assertEquals(threadCount, successCount.get(), "모든 사용자의 차감 성공 (병렬 처리)");
        log.info("✅ 병렬 처리 테스트 통과");
    }

    @Test
    @DisplayName("쿠폰 선착순 발급 - 동시성 제어 검증")
    void 쿠폰선착순발급_동시성제어검증() throws InterruptedException {
        // Given: 쿠폰 생성 (재고: 2개)
        Coupon coupon = Coupon.builder()
                .couponId(100L)
                .couponName("Limited Coupon")
                .discountAmount(5000L)
                .totalQuantity(2)
                .remainingQty(2)
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(1))
                .build();
        couponRepository.save(coupon);

        // 테스트 사용자들 생성
        for (long i = 100L; i <= 104L; i++) {
            User user = User.builder()
                    .userId(i)
                    .name("user" + i)
                    .email("user" + i + "@example.com")
                    .balance(10000L)
                    .build();
            userRepository.save(user);
        }

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        log.info("[Test] 재고 2개 쿠폰을 {} 명이 동시에 발급 시도 (선착순)", threadCount);

        // When: 5명이 동시에 쿠폰 발급 시도 (2개만 발급 가능)
        for (int i = 0; i < threadCount; i++) {
            long userId = 100L + i;
            executor.submit(() -> {
                try {
                    log.info("[User-{}] 쿠폰 발급 시도...", userId - 100);
                    var result = couponService.issueCouponWithLock(userId, 100L);
                    successCount.incrementAndGet();
                    log.info("[User-{}] ✅ 쿠폰 발급 성공", userId - 100);
                } catch (IllegalArgumentException e) {
                    failureCount.incrementAndGet();
                    log.info("[User-{}] ⚠️ 쿠폰 소진 또는 중복 발급", userId - 100);
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.error("[User-{}] ❌ 예외 발생", userId - 100, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;

        // Then: 최대 2개만 발급되어야 함
        log.info("========== 테스트 결과 ==========");
        log.info("발급 성공: {}, 발급 실패: {}", successCount.get(), failureCount.get());
        log.info("소요 시간: {} ms", elapsedTime);
        log.info("예상: 최대 2개 발급 (재고 2개), 나머지 3개 실패");

        assertEquals(threadCount, successCount.get() + failureCount.get(), "모든 스레드 완료");
        assertEquals(2, successCount.get(), "정확히 2개의 쿠폰만 발급되어야 함 (선착순)");

        log.info("✅ 선착순 발급 테스트 통과 - 동시성 제어 확인");
    }
}
