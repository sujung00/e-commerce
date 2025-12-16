package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.domain.order.ChildTxType;
import com.hhplus.ecommerce.domain.order.ExecutedChildTransaction;
import com.hhplus.ecommerce.domain.order.ExecutionStatus;
import com.hhplus.ecommerce.infrastructure.persistence.order.ExecutedChildTransactionJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 멱등성 토큰 비관적 락 동시성 제어 테스트
 *
 * TEST-004: 멱등성 토큰 비관적 락 동시성 제어
 *
 * 시나리오:
 * 1. 동일한 idempotencyToken으로 2개의 스레드가 동시 요청
 * 2. 첫 번째 스레드: findByIdempotencyTokenForUpdate() 락 획득
 * 3. 두 번째 스레드: 락 대기
 * 4. 첫 번째 스레드: 트랜잭션 완료 및 커밋
 * 5. 두 번째 스레드: 락 획득 후 이미 실행된 기록 확인 → skip
 *
 * 검증 조건:
 * - ✅ ExecutedChildTransaction 1건만 생성
 * - ✅ 두 번째 요청은 중복 실행 방지됨
 * - ✅ SELECT FOR UPDATE 락 동작 확인
 */
@SpringBootTest
@DisplayName("멱등성 토큰 비관적 락 동시성 제어 테스트")
class IdempotencyTokenConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private ExecutedChildTransactionJpaRepository executedChildTransactionRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate newTransactionTemplate;

    @Autowired
    public void setTransactionManager(PlatformTransactionManager tm) {
        this.newTransactionTemplate = new TransactionTemplate(tm);
        this.newTransactionTemplate.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    @Test
    @DisplayName("[TEST-004] 동일 멱등성 토큰으로 동시 요청 시 1건만 생성 (비관적 락)")
    void testIdempotencyToken_ConcurrentRequests_OnlyOneCreated() throws InterruptedException {
        // Given - 테스트 준비
        String idempotencyToken = UUID.randomUUID().toString();
        Long orderId = 1L;
        ChildTxType txType = ChildTxType.BALANCE_DEDUCT;
        int numThreads = 2;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger createdCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // When - 2개 스레드가 동시에 동일한 idempotencyToken으로 요청
        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i + 1;
            executor.submit(() -> {
                try {
                    // 모든 스레드가 동시에 시작하도록 대기
                    startLatch.await();

                    System.out.println("[Thread-" + threadIndex + "] 시작");

                    // REQUIRES_NEW 트랜잭션으로 실행
                    newTransactionTemplate.execute(status -> {
                        // Step 1: 비관적 락으로 조회
                        System.out.println("[Thread-" + threadIndex + "] 비관적 락 획득 시도");
                        Optional<ExecutedChildTransaction> existing =
                                executedChildTransactionRepository.findByIdempotencyTokenForUpdate(idempotencyToken);

                        if (existing.isPresent()) {
                            // 이미 존재하면 skip (중복 실행 방지)
                            System.out.println("[Thread-" + threadIndex + "] 이미 존재함 - SKIP");
                            skippedCount.incrementAndGet();
                        } else {
                            // 존재하지 않으면 생성
                            System.out.println("[Thread-" + threadIndex + "] 락 획득 성공 - 생성 시작");

                            // 약간의 지연을 추가하여 경합 상황 시뮬레이션
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }

                            ExecutedChildTransaction transaction = ExecutedChildTransaction.create(
                                    orderId,
                                    idempotencyToken,
                                    txType
                            );
                            executedChildTransactionRepository.save(transaction);
                            entityManager.flush();

                            System.out.println("[Thread-" + threadIndex + "] 생성 완료");
                            createdCount.incrementAndGet();
                        }
                        return null;
                    });

                    System.out.println("[Thread-" + threadIndex + "] 트랜잭션 커밋 완료");

                } catch (Exception e) {
                    System.err.println("[Thread-" + threadIndex + "] 예외 발생: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 모든 스레드 동시 시작
        System.out.println("[Main] 모든 스레드 동시 시작");
        startLatch.countDown();

        // 모든 스레드 완료 대기 (최대 10초)
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "모든 스레드가 10초 내에 완료되어야 함");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Then - 검증
        System.out.println("[Main] 검증 시작");
        System.out.println("[Main] createdCount: " + createdCount.get());
        System.out.println("[Main] skippedCount: " + skippedCount.get());

        // 검증 1: ExecutedChildTransaction 1건만 생성
        List<ExecutedChildTransaction> allRecords = newTransactionTemplate.execute(status ->
            executedChildTransactionRepository.findAll()
        );
        System.out.println("[Main] DB에 저장된 총 레코드 수: " + allRecords.size());

        long recordCount = newTransactionTemplate.execute(status ->
            executedChildTransactionRepository.count()
        );
        assertEquals(1, recordCount, "ExecutedChildTransaction이 정확히 1건만 생성되어야 함");

        // 검증 2: 생성 카운트와 skip 카운트 확인
        assertEquals(1, createdCount.get(), "1개 스레드만 생성해야 함");
        assertEquals(1, skippedCount.get(), "1개 스레드는 skip 되어야 함");

        // 검증 3: 생성된 레코드 확인
        ExecutedChildTransaction savedTransaction = newTransactionTemplate.execute(status ->
            executedChildTransactionRepository.findByIdempotencyToken(idempotencyToken).orElseThrow()
        );

        assertNotNull(savedTransaction, "생성된 레코드가 존재해야 함");
        assertEquals(idempotencyToken, savedTransaction.getIdempotencyToken(), "멱등성 토큰이 일치해야 함");
        assertEquals(orderId, savedTransaction.getOrderId(), "주문 ID가 일치해야 함");
        assertEquals(txType, savedTransaction.getTxType(), "TX 타입이 일치해야 함");
        assertEquals(ExecutionStatus.PENDING, savedTransaction.getStatus(), "상태가 PENDING이어야 함");

        System.out.println("[Main] 모든 검증 통과");
    }

    @Test
    @DisplayName("[TEST-004] 10개 스레드 동시 요청 시 1건만 생성 (스트레스 테스트)")
    void testIdempotencyToken_10ConcurrentRequests_OnlyOneCreated() throws InterruptedException {
        // Given - 테스트 준비
        String idempotencyToken = UUID.randomUUID().toString();
        Long orderId = 2L;
        ChildTxType txType = ChildTxType.INVENTORY_DEDUCT;
        int numThreads = 10;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger createdCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // When - 10개 스레드가 동시에 동일한 idempotencyToken으로 요청
        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i + 1;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    newTransactionTemplate.execute(status -> {
                        Optional<ExecutedChildTransaction> existing =
                                executedChildTransactionRepository.findByIdempotencyTokenForUpdate(idempotencyToken);

                        if (existing.isPresent()) {
                            skippedCount.incrementAndGet();
                        } else {
                            ExecutedChildTransaction transaction = ExecutedChildTransaction.create(
                                    orderId,
                                    idempotencyToken,
                                    txType
                            );
                            executedChildTransactionRepository.save(transaction);
                            entityManager.flush();
                            createdCount.incrementAndGet();
                        }
                        return null;
                    });

                } catch (Exception e) {
                    System.err.println("[Thread-" + threadIndex + "] 예외 발생: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 모든 스레드 동시 시작
        startLatch.countDown();

        // 모든 스레드 완료 대기 (최대 15초)
        boolean completed = endLatch.await(15, TimeUnit.SECONDS);
        assertTrue(completed, "모든 스레드가 15초 내에 완료되어야 함");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Then - 검증
        System.out.println("[10 Threads Test] createdCount: " + createdCount.get());
        System.out.println("[10 Threads Test] skippedCount: " + skippedCount.get());

        // 검증 1: ExecutedChildTransaction 1건만 생성
        long recordCount = newTransactionTemplate.execute(status -> {
            List<ExecutedChildTransaction> records = executedChildTransactionRepository.findByIdempotencyToken(idempotencyToken)
                    .map(List::of)
                    .orElse(List.of());
            return (long) records.size();
        });
        assertEquals(1, recordCount, "ExecutedChildTransaction이 정확히 1건만 생성되어야 함");

        // 검증 2: 생성 카운트와 skip 카운트 확인
        assertEquals(1, createdCount.get(), "1개 스레드만 생성해야 함");
        assertEquals(numThreads - 1, skippedCount.get(), (numThreads - 1) + "개 스레드는 skip 되어야 함");

        System.out.println("[10 Threads Test] 모든 검증 통과");
    }

    @Test
    @DisplayName("[TEST-004] 서로 다른 토큰은 각각 생성됨 (정상 동작)")
    void testIdempotencyToken_DifferentTokens_BothCreated() throws InterruptedException {
        // Given - 서로 다른 2개의 토큰
        String token1 = UUID.randomUUID().toString();
        String token2 = UUID.randomUUID().toString();
        Long orderId = 3L;
        ChildTxType txType = ChildTxType.COUPON_ISSUE;
        int numThreads = 2;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger createdCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // When - 2개 스레드가 서로 다른 토큰으로 동시 요청
        String[] tokens = {token1, token2};
        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    newTransactionTemplate.execute(status -> {
                        String token = tokens[threadIndex];
                        Optional<ExecutedChildTransaction> existing =
                                executedChildTransactionRepository.findByIdempotencyTokenForUpdate(token);

                        if (existing.isEmpty()) {
                            ExecutedChildTransaction transaction = ExecutedChildTransaction.create(
                                    orderId,
                                    token,
                                    txType
                            );
                            executedChildTransactionRepository.save(transaction);
                            entityManager.flush();
                            createdCount.incrementAndGet();
                        }
                        return null;
                    });

                } catch (Exception e) {
                    System.err.println("[Thread-" + threadIndex + "] 예외 발생: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 모든 스레드 동시 시작
        startLatch.countDown();

        // 모든 스레드 완료 대기
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "모든 스레드가 10초 내에 완료되어야 함");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Then - 검증: 서로 다른 토큰이므로 2건 모두 생성되어야 함
        assertEquals(2, createdCount.get(), "서로 다른 토큰이므로 2건 모두 생성되어야 함");

        // 각 토큰별로 레코드 확인
        ExecutedChildTransaction record1 = newTransactionTemplate.execute(status ->
            executedChildTransactionRepository.findByIdempotencyToken(token1).orElseThrow()
        );
        ExecutedChildTransaction record2 = newTransactionTemplate.execute(status ->
            executedChildTransactionRepository.findByIdempotencyToken(token2).orElseThrow()
        );

        assertNotNull(record1, "token1에 대한 레코드가 존재해야 함");
        assertNotNull(record2, "token2에 대한 레코드가 존재해야 함");
        assertEquals(token1, record1.getIdempotencyToken(), "token1이 일치해야 함");
        assertEquals(token2, record2.getIdempotencyToken(), "token2가 일치해야 함");

        System.out.println("[Different Tokens Test] 모든 검증 통과");
    }

    @Test
    @DisplayName("[TEST-004] COMPLETED 상태 레코드는 재시도 불가")
    void testIdempotencyToken_CompletedRecord_NotRetryable() throws InterruptedException {
        // Given - COMPLETED 상태의 레코드 생성
        String idempotencyToken = UUID.randomUUID().toString();
        Long orderId = 4L;
        ChildTxType txType = ChildTxType.BALANCE_DEDUCT;

        // 첫 번째 요청: COMPLETED 상태로 생성
        newTransactionTemplate.execute(status -> {
            ExecutedChildTransaction transaction = ExecutedChildTransaction.create(
                    orderId,
                    idempotencyToken,
                    txType
            );
            transaction.markAsCompleted("{\"result\": \"success\"}");
            executedChildTransactionRepository.save(transaction);
            entityManager.flush();
            return null;
        });

        // When - 동일 토큰으로 재시도
        int numThreads = 3;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger skippedCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    newTransactionTemplate.execute(status -> {
                        Optional<ExecutedChildTransaction> existing =
                                executedChildTransactionRepository.findByIdempotencyTokenForUpdate(idempotencyToken);

                        if (existing.isPresent() && !existing.get().isRetryable()) {
                            // COMPLETED 상태이므로 재시도 불가
                            skippedCount.incrementAndGet();
                        }
                        return null;
                    });

                } catch (Exception e) {
                    System.err.println("예외 발생: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Then - 검증: 모든 재시도가 skip 되어야 함
        assertEquals(numThreads, skippedCount.get(), "모든 재시도가 skip 되어야 함");

        // 레코드 개수는 1건 유지
        long recordCount = newTransactionTemplate.execute(status -> {
            List<ExecutedChildTransaction> records = executedChildTransactionRepository.findByIdempotencyToken(idempotencyToken)
                    .map(List::of)
                    .orElse(List.of());
            return (long) records.size();
        });
        assertEquals(1, recordCount, "레코드는 1건만 존재해야 함");

        System.out.println("[COMPLETED Record Test] 모든 검증 통과");
    }
}
