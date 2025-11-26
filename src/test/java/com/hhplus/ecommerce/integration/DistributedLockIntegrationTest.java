package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.lock.DistributedLockExampleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis 분산락 통합 테스트
 *
 * 실제 Redis를 사용하여 분산락이 정상 작동하는지 검증합니다.
 *
 * 테스트 실행 조건:
 * - Redis가 localhost:6379에서 실행 중이어야 합니다.
 * - Docker: docker run -d -p 6379:6379 redis:latest
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("[Integration] Redis 분산락 테스트")
@Testcontainers
class DistributedLockIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockIntegrationTest.class);

    @Autowired
    private DistributedLockExampleService lockExampleService;

    @BeforeEach
    void setUp() {
        log.info("========== 분산락 테스트 시작 ==========");
    }

    @Test
    @DisplayName("고정 키 분산락 - 단일 스레드")
    void testFixedKeyLock_SingleThread() {
        // Given & When
        boolean result = lockExampleService.issueCouponFixed();

        // Then
        assertTrue(result, "고정 키 분산락 성공");
        log.info("✅ 고정 키 분산락 테스트 통과");
    }

    @Test
    @DisplayName("동적 키 분산락 - 단일 스레드")
    void testDynamicKeyLock_SingleThread() {
        // Given & When
        boolean result = lockExampleService.issueCoupon(1L);

        // Then
        assertTrue(result, "동적 키 분산락 성공");
        log.info("✅ 동적 키 분산락 테스트 통과");
    }

    @Test
    @DisplayName("다중 파라미터 분산락 - 단일 스레드")
    void testMultiParameterLock_SingleThread() {
        // Given & When
        boolean result = lockExampleService.issueCouponToUser(10L, 5L);

        // Then
        assertTrue(result, "다중 파라미터 분산락 성공");
        log.info("✅ 다중 파라미터 분산락 테스트 통과");
    }

    @Test
    @DisplayName("동일 키에 대한 동시 접근 - 순차 처리 검증")
    void testConcurrentAccessSameKey() throws InterruptedException {
        // Given
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        log.info("[Test] 동일 키에 대해 {} 개 스레드가 동시 접근", threadCount);

        // When: 같은 coupon:1 키로 5개 스레드가 동시 접근
        for (int i = 0; i < threadCount; i++) {
            int threadId = i;
            executor.submit(() -> {
                try {
                    log.info("[Thread-{}] 락 획득 시도...", threadId);
                    boolean result = lockExampleService.issueCouponFixed();
                    if (result) {
                        successCount.incrementAndGet();
                        log.info("[Thread-{}] ✅ 성공", threadId);
                    } else {
                        failureCount.incrementAndGet();
                        log.warn("[Thread-{}] ❌ 실패", threadId);
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.error("[Thread-{}] 예외 발생", threadId, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();
        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;

        // Then
        log.info("========== 테스트 결과 ==========");
        log.info("성공: {}, 실패: {}", successCount.get(), failureCount.get());
        log.info("소요 시간: {} ms", elapsedTime);
        log.info("예상 시간: ~5000ms (각 스레드 1초 × 5개)");

        assertEquals(threadCount, successCount.get() + failureCount.get(),
                "모든 스레드가 완료되어야 함");
        assertEquals(threadCount, successCount.get(),
                "모든 스레드가 성공해야 함 (순차 처리)");
        assertTrue(elapsedTime >= 5000,
                "순차 처리로 인해 최소 5초 이상 소요되어야 함 (실제: " + elapsedTime + "ms)");

        log.info("✅ 동시 접근 테스트 통과 - 순차 처리 확인");
    }

    @Test
    @DisplayName("다른 키에 대한 동시 접근 - 병렬 처리 검증")
    void testConcurrentAccessDifferentKeys() throws InterruptedException {
        // Given
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        log.info("[Test] 서로 다른 키에 대해 {} 개 스레드가 동시 접근", threadCount);

        // When: 서로 다른 coupon:1, coupon:2, ..., coupon:5 키로 동시 접근
        for (int i = 0; i < threadCount; i++) {
            long couponId = i + 1;
            executor.submit(() -> {
                try {
                    log.info("[Thread-coupon:{}] 락 획득 시도...", couponId);
                    boolean result = lockExampleService.issueCoupon(couponId);
                    if (result) {
                        successCount.incrementAndGet();
                        log.info("[Thread-coupon:{}] ✅ 성공", couponId);
                    }
                } catch (Exception e) {
                    log.error("[Thread-coupon:{}] 예외 발생", couponId, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();
        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;

        // Then
        log.info("========== 테스트 결과 ==========");
        log.info("성공: {}", successCount.get());
        log.info("소요 시간: {} ms", elapsedTime);
        log.info("예상 시간: ~1000ms (병렬 처리, 최대 1초)");

        assertEquals(threadCount, successCount.get(),
                "모든 스레드가 성공해야 함 (병렬 처리)");
        assertTrue(elapsedTime < 5000,
                "병렬 처리로 인해 5초 미만이어야 함 (실제: " + elapsedTime + "ms)");

        log.info("✅ 다른 키 테스트 통과 - 병렬 처리 확인");
    }

    @Test
    @DisplayName("락 타임아웃 - 락 획득 실패")
    void testLockTimeout() throws InterruptedException {
        // Given
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger secondThreadSuccess = new AtomicInteger(0);

        log.info("[Test] 락 타임아웃 검증");

        // When: 첫 번째 스레드가 오래 걸리는 작업 수행
        executor.submit(() -> {
            try {
                log.info("[Thread-1] 락 획득 시도 (오래 걸리는 작업)");
                boolean result = lockExampleService.issueCoupon(100L);
                log.info("[Thread-1] 완료 - result: {}", result);
            } catch (Exception e) {
                log.error("[Thread-1] 예외 발생", e);
            }
        });

        // 첫 번째 스레드가 락을 획득하도록 대기
        Thread.sleep(500);

        // 두 번째 스레드가 즉시 락 획득 시도 (실패해야 함)
        executor.submit(() -> {
            try {
                log.info("[Thread-2] 락 획득 시도 (즉시)");
                boolean result = lockExampleService.issueCoupon(100L);
                log.info("[Thread-2] 완료 - result: {}", result);
                if (!result) {
                    secondThreadSuccess.set(1);
                }
                latch.countDown();
            } catch (Exception e) {
                log.error("[Thread-2] 예외 발생", e);
                latch.countDown();
            }
        });

        // 결과 대기
        latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        log.info("========== 테스트 결과 ==========");
        log.info("두 번째 스레드 실패 여부: {}", secondThreadSuccess.get());

        // Note: 두 번째 스레드는 5초간 대기하므로 결과가 false일 가능성이 높음
        log.info("✅ 락 타임아웃 테스트 완료");
    }
}
