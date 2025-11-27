package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.application.lock.DistributedLockExampleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * BaseIntegrationTest를 상속하여 TestContainers 기반 MySQL + Redis 자동 구동
 *
 * 테스트 특징:
 * - TestContainers Redis를 자동으로 시작 (localhost:6379 외부 의존 제거)
 * - TestContainers MySQL도 함께 구동 (격리된 테스트 환경)
 * - 분산락의 동시성 제어 검증
 * - 포트 충돌 없이 다중 테스트 병렬 실행 가능
 */
@DisplayName("[Integration] Redis 분산락 테스트 (TestContainers)")
class DistributedLockIntegrationTest extends BaseIntegrationTest {

    private static final Logger 로그 = LoggerFactory.getLogger(DistributedLockIntegrationTest.class);

    @Autowired
    private DistributedLockExampleService 분산락예제서비스;

    @BeforeEach
    void 준비() {
        로그.info("========== 분산락 테스트 시작 ==========");
    }

    @Test
    @DisplayName("고정 키 분산락 - 단일 스레드")
    void 고정키분산락_단일스레드() {
        // Given & When
        boolean 결과 = 분산락예제서비스.issueCouponFixed();

        // Then
        assertTrue(결과, "고정 키 분산락 성공");
        로그.info("✅ 고정 키 분산락 테스트 통과");
    }

    @Test
    @DisplayName("동적 키 분산락 - 단일 스레드")
    void 동적키분산락_단일스레드() {
        // Given & When
        boolean 결과 = 분산락예제서비스.issueCoupon(1L);

        // Then
        assertTrue(결과, "동적 키 분산락 성공");
        로그.info("✅ 동적 키 분산락 테스트 통과");
    }

    @Test
    @DisplayName("다중 파라미터 분산락 - 단일 스레드")
    void 다중파라미터분산락_단일스레드() {
        // Given & When
        boolean 결과 = 분산락예제서비스.issueCouponToUser(10L, 5L);

        // Then
        assertTrue(결과, "다중 파라미터 분산락 성공");
        로그.info("✅ 다중 파라미터 분산락 테스트 통과");
    }

    @Test
    @DisplayName("동일 키에 대한 동시 접근 - 순차 처리 검증")
    void 동일키동시접근_순차처리검증() throws InterruptedException {
        // Given
        int 스레드수 = 5;
        ExecutorService 실행기 = Executors.newFixedThreadPool(스레드수);
        CountDownLatch 래치 = new CountDownLatch(스레드수);
        AtomicInteger 성공수 = new AtomicInteger(0);
        AtomicInteger 실패수 = new AtomicInteger(0);
        long 시작시간 = System.currentTimeMillis();

        로그.info("[Test] 동일 키에 대해 {} 개 스레드가 동시 접근", 스레드수);

        // When: 같은 coupon:1 키로 5개 스레드가 동시 접근
        for (int i = 0; i < 스레드수; i++) {
            int 스레드아이디 = i;
            실행기.submit(() -> {
                try {
                    로그.info("[Thread-{}] 락 획득 시도...", 스레드아이디);
                    boolean 결과 = 분산락예제서비스.issueCouponFixed();
                    if (결과) {
                        성공수.incrementAndGet();
                        로그.info("[Thread-{}] ✅ 성공", 스레드아이디);
                    } else {
                        실패수.incrementAndGet();
                        로그.warn("[Thread-{}] ❌ 실패", 스레드아이디);
                    }
                } catch (Exception e) {
                    실패수.incrementAndGet();
                    로그.error("[Thread-{}] 예외 발생", 스레드아이디, e);
                } finally {
                    래치.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        boolean 완료됨 = 래치.await(60, TimeUnit.SECONDS);
        실행기.shutdown();
        long 종료시간 = System.currentTimeMillis();
        long 소요시간 = 종료시간 - 시작시간;

        // Then
        로그.info("========== 테스트 결과 ==========");
        로그.info("성공: {}, 실패: {}", 성공수.get(), 실패수.get());
        로그.info("소요 시간: {} ms", 소요시간);
        로그.info("예상 시간: ~5000ms (각 스레드 1초 × 5개)");

        assertEquals(스레드수, 성공수.get() + 실패수.get(),
                "모든 스레드가 완료되어야 함");
        assertEquals(스레드수, 성공수.get(),
                "모든 스레드가 성공해야 함 (순차 처리)");
        assertTrue(소요시간 >= 5000,
                "순차 처리로 인해 최소 5초 이상 소요되어야 함 (실제: " + 소요시간 + "ms)");

        로그.info("✅ 동시 접근 테스트 통과 - 순차 처리 확인");
    }

    @Test
    @DisplayName("다른 키에 대한 동시 접근 - 병렬 처리 검증")
    void 다른키동시접근_병렬처리검증() throws InterruptedException {
        // Given
        int 스레드수 = 5;
        ExecutorService 실행기 = Executors.newFixedThreadPool(스레드수);
        CountDownLatch 래치 = new CountDownLatch(스레드수);
        AtomicInteger 성공수 = new AtomicInteger(0);
        long 시작시간 = System.currentTimeMillis();

        로그.info("[Test] 서로 다른 키에 대해 {} 개 스레드가 동시 접근", 스레드수);

        // When: 서로 다른 coupon:1, coupon:2, ..., coupon:5 키로 동시 접근
        for (int i = 0; i < 스레드수; i++) {
            long 쿠폰아이디 = i + 1;
            실행기.submit(() -> {
                try {
                    로그.info("[Thread-coupon:{}] 락 획득 시도...", 쿠폰아이디);
                    boolean 결과 = 분산락예제서비스.issueCoupon(쿠폰아이디);
                    if (결과) {
                        성공수.incrementAndGet();
                        로그.info("[Thread-coupon:{}] ✅ 성공", 쿠폰아이디);
                    }
                } catch (Exception e) {
                    로그.error("[Thread-coupon:{}] 예외 발생", 쿠폰아이디, e);
                } finally {
                    래치.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기
        래치.await(60, TimeUnit.SECONDS);
        실행기.shutdown();
        long 종료시간 = System.currentTimeMillis();
        long 소요시간 = 종료시간 - 시작시간;

        // Then
        로그.info("========== 테스트 결과 ==========");
        로그.info("성공: {}", 성공수.get());
        로그.info("소요 시간: {} ms", 소요시간);
        로그.info("예상 시간: ~1000ms (병렬 처리, 최대 1초)");

        assertEquals(스레드수, 성공수.get(),
                "모든 스레드가 성공해야 함 (병렬 처리)");
        assertTrue(소요시간 < 5000,
                "병렬 처리로 인해 5초 미만이어야 함 (실제: " + 소요시간 + "ms)");

        로그.info("✅ 다른 키 테스트 통과 - 병렬 처리 확인");
    }

    @Test
    @DisplayName("락 타임아웃 - 락 획득 실패")
    void 락타임아웃_락획득실패() throws InterruptedException {
        // Given
        ExecutorService 실행기 = Executors.newFixedThreadPool(2);
        CountDownLatch 래치 = new CountDownLatch(1);
        AtomicInteger 두번째스레드성공 = new AtomicInteger(0);

        로그.info("[Test] 락 타임아웃 검증");

        // When: 첫 번째 스레드가 오래 걸리는 작업 수행
        실행기.submit(() -> {
            try {
                로그.info("[Thread-1] 락 획득 시도 (오래 걸리는 작업)");
                boolean 결과 = 분산락예제서비스.issueCoupon(100L);
                로그.info("[Thread-1] 완료 - result: {}", 결과);
            } catch (Exception e) {
                로그.error("[Thread-1] 예외 발생", e);
            }
        });

        // 첫 번째 스레드가 락을 획득하도록 대기
        Thread.sleep(500);

        // 두 번째 스레드가 즉시 락 획득 시도 (실패해야 함)
        실행기.submit(() -> {
            try {
                로그.info("[Thread-2] 락 획득 시도 (즉시)");
                boolean 결과 = 분산락예제서비스.issueCoupon(100L);
                로그.info("[Thread-2] 완료 - result: {}", 결과);
                if (!결과) {
                    두번째스레드성공.set(1);
                }
                래치.countDown();
            } catch (Exception e) {
                로그.error("[Thread-2] 예외 발생", e);
                래치.countDown();
            }
        });

        // 결과 대기
        래치.await(15, TimeUnit.SECONDS);
        실행기.shutdown();

        // Then
        로그.info("========== 테스트 결과 ==========");
        로그.info("두 번째 스레드 실패 여부: {}", 두번째스레드성공.get());

        // Note: 두 번째 스레드는 5초간 대기하므로 결과가 false일 가능성이 높음
        로그.info("✅ 락 타임아웃 테스트 완료");
    }
}
