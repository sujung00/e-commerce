package com.hhplus.ecommerce.infrastructure.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 분산락 AOP 처리 (TransactionSynchronization 기반)
 *
 * ⚠️ CRITICAL: Lock 해제 시점 보장
 * ===========================================
 * Lock을 획득하면서도 @Transactional 메서드의 트랜잭션이 완벽하게 커밋된 후에만
 * Lock이 해제되어야 합니다. 이를 위해 TransactionSynchronization을 사용합니다.
 *
 * 실행 순서 (명시적 보장):
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │ 1. Lock 획득 (DistributedLockAop)                                   │
 * │    @Order(-1000): 가장 먼저 실행됨                                  │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │ 2. @Transactional 시작                                              │
 * │    propagation = REQUIRES_NEW: 새로운 독립적인 트랜잭션 생성        │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │ 3. 비즈니스 로직 실행                                                │
 * │    DB 레벨 비관적 락 (SELECT...FOR UPDATE) 적용                    │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │ 4. @Transactional 커밋/롤백                                         │
 * │    TransactionSynchronization 콜백 등록됨                          │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │ 5. 트랜잭션 완전히 종료 후 Lock 해제                                │
 * │    TransactionSynchronization.afterCompletion() 호출              │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * 동시성 제어 전략:
 * - Redis 분산락: 분산 환경에서 여러 서버의 동시 접근 방지
 * - DB 비관적 락: 단일 DB 내에서 Lost Update 방지
 * - TransactionSynchronization: 트랜잭션 완료 후 Lock 해제
 * - REQUIRES_NEW: 각 메서드가 독립적인 트랜잭션 경계
 *
 * @DistributedLock 어노테이션이 붙은 메서드 호출 시:
 * 1. 동적 키 생성 (Spring EL 지원)
 * 2. Redis 분산락 획득 시도 (타임아웃 처리)
 * 3. TransactionSynchronization 콜백 등록
 * 4. 메서드 실행 (@Transactional 트랜잭션 시작)
 * 5. 트랜잭션 커밋/롤백
 * 6. TransactionSynchronization 콜백 실행 → Lock 해제
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
@org.springframework.core.annotation.Order(Ordered.LOWEST_PRECEDENCE - 1000)
public class DistributedLockAop implements Ordered {

    private final RedissonClient redissonClient;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * @DistributedLock 어노테이션이 붙은 메서드를 인터셉트하여 분산락을 적용합니다.
     *
     * ✅ 주요 개선:
     * 1. @Order(LOWEST_PRECEDENCE - 1000) → @Transactional보다 먼저 실행 보장
     * 2. TransactionSynchronization 사용 → 트랜잭션 완료 후 Lock 해제
     * 3. 실행 순서 보장:
     *    Lock 획득 → @Transactional 시작 → 비즈니스로직 → @Transactional 종료 → Lock 해제
     * 4. 동시성 시나리오 처리:
     *    - 정상 커밋: TransactionSynchronization.afterCompletion(STATUS_COMMITTED)
     *    - 롤백 발생: TransactionSynchronization.afterCompletion(STATUS_ROLLED_BACK)
     *    - 트랜잭션 없음: finally 블록에서 lock 해제 (for non-transactional methods)
     *
     * ⚠️ 주의: 이 메서드는 반드시 @Transactional(propagation=REQUIRES_NEW)과 함께 사용되어야 합니다.
     */
    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String dynamicKey = generateKey(joinPoint, distributedLock.key());
        RLock rLock = redissonClient.getLock(dynamicKey);

        boolean lockAcquired = false;
        try {
            // ============ STEP 1: Lock 획득 ============
            log.debug("[DistributedLock] 락 획득 시도 - key: {}, waitTime: {}s, leaseTime: {}s",
                    dynamicKey,
                    distributedLock.waitTime(),
                    distributedLock.leaseTime());

            lockAcquired = rLock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );

            if (!lockAcquired) {
                log.warn("[DistributedLock] 락 획득 실패 - key: {} (다른 스레드가 소유 중)", dynamicKey);
                throw new RuntimeException(
                        String.format("[DistributedLock] 락 획득 실패 - key: %s (waitTime 초과)", dynamicKey)
                );
            }

            log.info("[DistributedLock] 락 획득 성공 - key: {}", dynamicKey);

            // ============ STEP 2: TransactionSynchronization 콜백 등록 ============
            // 트랜잭션이 존재하는 경우 (즉, @Transactional이 붙어있는 메서드)
            // TransactionSynchronization 콜백을 등록하여 트랜잭션 완료 후 Lock 해제
            // 트랜잭션이 없는 경우 finally 블록에서 Lock 해제됨
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new LockReleaseSynchronization(rLock, dynamicKey)
                );
                log.debug("[DistributedLock] TransactionSynchronization 콜백 등록 - key: {}", dynamicKey);
            }

            // ============ STEP 3: @Transactional 시작 & 비즈니스 로직 실행 ============
            // joinPoint.proceed()를 호출하면:
            // - Spring이 @Transactional을 감지하고 트랜잭션 시작 (또는 기존 트랜잭션 재사용)
            // - 비즈니스 로직 실행
            // - 예외 없으면 트랜잭션 커밋
            // - 예외 발생하면 트랜잭션 롤백 (Spring이 자동 처리)
            try {
                log.debug("[DistributedLock] 메서드 실행 시작 (트랜잭션 내부) - key: {}", dynamicKey);
                Object result = joinPoint.proceed();
                log.debug("[DistributedLock] 메서드 실행 완료 (트랜잭션 커밋됨) - key: {}", dynamicKey);
                return result;

            } catch (Throwable throwable) {
                // ❌ 예외 발생 → 트랜잭션 롤백 (Spring이 자동 처리)
                // TransactionSynchronization 콜백이 등록되었다면 afterCompletion()이 호출됨
                log.error("[DistributedLock] 메서드 실행 중 예외 발생 - key: {}, exception: {}",
                        dynamicKey, throwable.getClass().getSimpleName(), throwable);
                throw throwable;
            }

        } catch (InterruptedException e) {
            // 락 대기 중 Thread.interrupt() 호출됨
            Thread.currentThread().interrupt();
            log.error("[DistributedLock] 락 대기 중 스레드 인터럽트 - key: {}", dynamicKey, e);
            throw new RuntimeException(
                    String.format("[DistributedLock] 락 대기 중 인터럽트 - key: %s", dynamicKey),
                    e
            );

        } finally {
            // ============ STEP 4: Lock 해제 (트랜잭션 없는 경우만) ============
            // TransactionSynchronization이 등록되었으면 이 블록에서는 해제하지 않음
            // (트랜잭션 완료 후 콜백에서 해제됨)
            //
            // TransactionSynchronization이 없는 경우 (non-transactional methods):
            // - 이 finally 블록에서 Lock을 해제
            if (lockAcquired && rLock.isHeldByCurrentThread()) {
                // TransactionSynchronization이 등록되었는지 확인
                // (등록되었으면 콜백에서 처리되므로 여기서는 스킵)
                if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                    try {
                        rLock.unlock();
                        log.info("[DistributedLock] 락 해제 성공 (non-transactional) - key: {}", dynamicKey);
                    } catch (Exception unlockError) {
                        log.error("[DistributedLock] 락 해제 중 오류 발생 - key: {}", dynamicKey, unlockError);
                    }
                }
            }
        }
    }

    /**
     * 트랜잭션 완료 후 Lock을 해제하는 TransactionSynchronization 구현
     *
     * 트랜잭션이 커밋되거나 롤백된 후에 호출되어 Lock을 안전하게 해제합니다.
     */
    private static class LockReleaseSynchronization implements TransactionSynchronization {
        private final RLock rLock;
        private final String lockKey;
        private boolean lockReleased = false;

        public LockReleaseSynchronization(RLock rLock, String lockKey) {
            this.rLock = rLock;
            this.lockKey = lockKey;
        }

        /**
         * 트랜잭션 완료 후 호출됨 (커밋 또는 롤백 모두)
         *
         * @param status TransactionSynchronization.STATUS_COMMITTED (커밋 성공)
         *               TransactionSynchronization.STATUS_ROLLED_BACK (롤백)
         *               TransactionSynchronization.STATUS_UNKNOWN (불명확)
         */
        @Override
        public void afterCompletion(int status) {
            if (lockReleased) {
                return;  // 중복 해제 방지
            }

            try {
                if (rLock.isHeldByCurrentThread()) {
                    rLock.unlock();
                    lockReleased = true;

                    String statusString = switch (status) {
                        case STATUS_COMMITTED -> "COMMITTED";
                        case STATUS_ROLLED_BACK -> "ROLLED_BACK";
                        default -> "UNKNOWN";
                    };

                    log.info("[DistributedLock] 락 해제 성공 (TransactionSynchronization, status={}) - key: {}",
                            statusString, lockKey);
                }
            } catch (Exception e) {
                log.error("[DistributedLock] 락 해제 중 오류 발생 (TransactionSynchronization) - key: {}",
                        lockKey, e);
            }
        }
    }

    /**
     * Spring EL을 사용하여 동적 키를 생성합니다.
     *
     * 예제:
     * - "coupon:1" → "coupon:1"
     * - "coupon:#p0" → "coupon:5" (첫 번째 파라미터가 5일 때)
     * - "user:#p0:coupon:#p1" → "user:10:coupon:5"
     */
    private String generateKey(ProceedingJoinPoint joinPoint, String keyPattern) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] paramNames = signature.getParameterNames();

        // Spring EL 평가 컨텍스트 생성
        EvaluationContext context = new StandardEvaluationContext();

        // 파라미터를 컨텍스트에 추가
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable("p" + i, args[i]);
        }

        // 전체 args 배열도 컨텍스트에 추가
        context.setVariable("args", args);

        // Spring EL 표현식 평가
        try {
            String dynamicKey = expressionParser.parseExpression(keyPattern)
                    .getValue(context, String.class);
            log.debug("[DistributedLock] 동적 키 생성 - pattern: {}, result: {}", keyPattern, dynamicKey);
            return dynamicKey;
        } catch (Exception e) {
            log.error("[DistributedLock] 동적 키 생성 실패 - pattern: {}", keyPattern, e);
            // 실패 시 패턴 자체를 키로 사용
            return keyPattern;
        }
    }

    /**
     * @Order 우선순위 반환
     * - Ordered.LOWEST_PRECEDENCE - 1000은 매우 낮은 값
     * - 낮은 값일수록 높은 우선순위 (먼저 실행)
     * - @Transactional (order=0)보다 먼저 실행됨
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1000;
    }
}
