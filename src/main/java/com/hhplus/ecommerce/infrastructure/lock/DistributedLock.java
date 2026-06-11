package com.hhplus.ecommerce.infrastructure.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * DistributedLock - Redis 분산락 어노테이션
 *
 * 역할:
 * - Redisson 기반 분산락을 AOP로 적용하는 마커 어노테이션
 * - 멀티 인스턴스 환경에서 동일 자원에 대한 동시 접근 제어
 *
 * 동작 원리:
 * - DistributedLockAspect가 @Around로 감지
 * - RLock.tryLock(waitTime, leaseTime, timeUnit) 으로 락 획득 시도
 * - 획득 실패 시 RuntimeException 발생
 * - 메서드 실행 후 finally 블록에서 락 해제
 *
 * key SpEL 표현식 예시:
 * - "'coupon:stock:' + #request.couponId"   → coupon:stock:5
 * - "'user:balance:' + #userId"              → user:balance:10
 * - LockKeyGenerator.COUPON_STOCK_KEY_TEMPLATE 상수 참조 가능
 *
 * 주의:
 * - Spring AOP 프록시를 통해서만 동작 → public 메서드에만 적용
 * - self-invocation(같은 클래스 내 직접 호출)에서는 동작하지 않음
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /** 락 키 (SpEL 표현식) */
    String key();

    /** 락 획득 대기 시간 (LockConstants.DEFAULT_LOCK_WAIT_TIME = 5) */
    long waitTime() default 5L;

    /** 락 자동 해제 시간 (LockConstants.DEFAULT_LOCK_LEASE_TIME = 2, deadlock 방지) */
    long leaseTime() default 2L;

    /** 시간 단위 */
    java.util.concurrent.TimeUnit timeUnit() default TimeUnit.SECONDS;
}
