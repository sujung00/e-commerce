package com.hhplus.ecommerce.infrastructure.lock;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * DistributedLockAspect - @DistributedLock AOP 처리기
 *
 * 역할:
 * - @DistributedLock 어노테이션이 붙은 public 메서드를 @Around로 가로채기
 * - SpEL로 락 키 표현식을 실제 값으로 평가
 * - RLock.tryLock()으로 락 획득 → 메서드 실행 → finally에서 락 해제
 *
 * SpEL 키 평가:
 * - 메서드 파라미터를 이름(#paramName)과 위치(#p0, #p1, ...)로 모두 등록
 * - 예: key = "'coupon:stock:' + #request.couponId"
 *      → request 파라미터의 couponId 필드 값을 추출해 "coupon:stock:5" 생성
 *
 * 락 해제 조건:
 * - isHeldByCurrentThread() 확인 후 unlock() — leaseTime 초과로 자동 해제된 경우를 방어
 */
@Aspect
@Component
public class DistributedLockAspect {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockAspect.class);

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();

    public DistributedLockAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String lockKey = resolveKey(joinPoint, distributedLock.key());
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = lock.tryLock(
                distributedLock.waitTime(),
                distributedLock.leaseTime(),
                distributedLock.timeUnit()
        );

        if (!acquired) {
            log.warn("[DistributedLock] 락 획득 실패 - key={}", lockKey);
            throw new RuntimeException("분산락 획득에 실패했습니다: " + lockKey);
        }

        log.debug("[DistributedLock] 락 획득 - key={}", lockKey);

        try {
            return joinPoint.proceed();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[DistributedLock] 락 해제 - key={}", lockKey);
            }
        }
    }

    /**
     * SpEL 표현식을 실제 락 키 문자열로 평가한다.
     *
     * 파라미터를 두 가지 방식으로 컨텍스트에 등록:
     * - 이름 기반: #request, #userId 등 (파라미터명 보존 컴파일 필요)
     * - 위치 기반: #p0, #p1, ... (파라미터명 미보존 환경 대비)
     */
    private String resolveKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
            context.setVariable("p" + i, args[i]);
        }

        return parser.parseExpression(keyExpression).getValue(context, String.class);
    }
}
