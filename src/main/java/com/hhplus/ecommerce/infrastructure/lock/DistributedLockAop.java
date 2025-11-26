package com.hhplus.ecommerce.infrastructure.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 분산락 AOP 처리
 *
 * @DistributedLock 어노테이션이 붙은 메서드 호출 시:
 * 1. 동적 키 생성 (Spring EL 지원)
 * 2. Redis 분산락 획득 시도
 * 3. 락 획득 성공 시 메서드 실행, 실패 시 false 반환
 * 4. 메서드 실행 후 finally 블록에서 락 해제
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class DistributedLockAop {

    private final RedissonClient redissonClient;
    private final AopForTransaction aopForTransaction;

    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * @DistributedLock 어노테이션이 붙은 메서드를 인터셉트하여 분산락을 적용합니다.
     */
    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String dynamicKey = generateKey(joinPoint, distributedLock.key());
        RLock rLock = redissonClient.getLock(dynamicKey);

        boolean lockAcquired = false;
        try {
            // 락 획득 시도
            lockAcquired = rLock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );

            if (!lockAcquired) {
                log.warn("[DistributedLock] 락 획득 실패 - key: {}", dynamicKey);
                throw new RuntimeException(String.format("[DistributedLock] 락 획득 실패 - key: %s", dynamicKey));
            }

            log.debug("[DistributedLock] 락 획득 성공 - key: {}", dynamicKey);

            // 분리된 트랜잭션에서 메서드 실행
            return aopForTransaction.proceed(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            });

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[DistributedLock] 락 대기 중 인터럽트 - key: {}", dynamicKey, e);
            throw new RuntimeException(String.format("[DistributedLock] 락 대기 중 인터럽트 - key: %s", dynamicKey), e);
        } catch (Exception e) {
            log.error("[DistributedLock] 메서드 실행 중 예외 발생 - key: {}", dynamicKey, e);
            throw e;
        } finally {
            // 락 해제
            if (lockAcquired && rLock.isHeldByCurrentThread()) {
                rLock.unlock();
                log.debug("[DistributedLock] 락 해제 - key: {}", dynamicKey);
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
}
