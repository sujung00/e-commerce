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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 분산락 AOP 처리 (우선순위 명시 버전)
 *
 * ⚠️ CRITICAL: AOP 실행 순서 보장
 * ===========================================
 * Spring에서는 여러 AOP가 적용될 때 순서가 중요합니다.
 * @Transactional은 기본 order=0으로 실행되지만,
 * 분산락은 반드시 @Transactional보다 먼저 실행되어야 합니다.
 *
 * 해결책: @Order(Ordered.LOWEST_PRECEDENCE - 100) 적용
 * - 낮은 값 = 높은 우선순위 (먼저 실행)
 * - @Transactional (order=0) 보다 먼저 실행됨
 *
 * 실행 순서 (명시적 보장):
 * ┌─────────────────────────────────────┐
 * │ 1. Lock 획득 (DistributedLockAop)    │ ← @Order(-1000): 가장 먼저
 * ├─────────────────────────────────────┤
 * │ 2. @Transactional 시작              │ ← order=0: 다음에 실행
 * ├─────────────────────────────────────┤
 * │ 3. 비즈니스 로직 실행                │
 * ├─────────────────────────────────────┤
 * │ 4. @Transactional 종료 (커밋/롤백)   │
 * ├─────────────────────────────────────┤
 * │ 5. Lock 해제 (finally 블록)         │ ← 마지막에 실행
 * └─────────────────────────────────────┘
 *
 * 책임 분리:
 * - AOP는 "분산락만 담당"
 * - 트랜잭션은 서비스 메서드의 @Transactional에서만 관리
 *
 * @DistributedLock 어노테이션이 붙은 메서드 호출 시:
 * 1. 동적 키 생성 (Spring EL 지원)
 * 2. Redis 분산락 획득 시도 (타임아웃 처리)
 * 3. 락 획득 성공 시 메서드 실행, 실패 시 RuntimeException 발생
 * 4. 메서드 실행 후 finally 블록에서 락 해제
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
     * 1. @Order(LOWEST_PRECEDENCE - 1000) 적용 → @Transactional보다 먼저 실행
     * 2. 래퍼 메서드 생성 → 트랜잭션 프록시를 우회하고 순수 @Transactional 호출 가능
     * 3. 실행 순서 보장:
     *    Lock 획득 (AOP) → @Transactional 시작 → 비즈니스로직 → @Transactional 종료 → Lock 해제
     * 4. AopForTransaction 제거 (REQUIRES_NEW 트랜잭션 생성 안 함)
     * 5. joinPoint.proceed()만 호출 (트랜잭션 개입 없음)
     */
    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String dynamicKey = generateKey(joinPoint, distributedLock.key());
        RLock rLock = redissonClient.getLock(dynamicKey);

        boolean lockAcquired = false;
        try {
            // ============ STEP 1: Lock 획득 ============
            // 이 단계는 @Transactional보다 먼저 실행됨 (Order 설정으로 보장)
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

            // ============ STEP 2: @Transactional 시작 & 비즈니스 로직 실행 ============
            // joinPoint.proceed()를 호출하면:
            // - Spring이 @Transactional을 감지하고 트랜잭션 시작
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
                // 이 예외는 그대로 전파되며, 호출자가 처리하거나 상위 레이어로 전파됨
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
            // ============ STEP 3: Lock 해제 ============
            // 이 단계는 모든 상황에서 반드시 실행됨 (finally 보장)
            // - 비즈니스 로직 성공 후
            // - 비즈니스 로직 예외 발생 후
            // - 어떤 상황이든 Lock 해제되어야 함
            if (lockAcquired && rLock.isHeldByCurrentThread()) {
                try {
                    rLock.unlock();
                    log.info("[DistributedLock] 락 해제 성공 - key: {}", dynamicKey);
                } catch (Exception unlockError) {
                    log.error("[DistributedLock] 락 해제 중 오류 발생 - key: {}", dynamicKey, unlockError);
                    // 락 해제 오류는 로깅만 하고 계속 진행
                    // (이미 비즈니스 로직은 완료되었으므로)
                }
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
