package com.hhplus.ecommerce.infrastructure.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 분산락 어노테이션
 *
 * 메서드에 붙여서 Redis 기반 분산락을 적용합니다.
 * Spring EL을 지원하므로 메서드 파라미터를 동적 키로 사용할 수 있습니다.
 *
 * 예제:
 * @DistributedLock(key = "coupon:#p0", waitTime = 5, leaseTime = 2)
 * public void issueCoupon(Long couponId) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * Redis 분산락 키
     *
     * Spring EL 지원:
     * - #p0, #p1, ... : 메서드 파라미터 (위치 기반)
     * - #root.args[0], #root.args[1] : 메서드 파라미터 (인덱스 기반)
     *
     * 예제:
     * - "coupon:1" : 고정 키
     * - "coupon:#p0" : 첫 번째 파라미터를 키로 사용
     * - "user:#p0:coupon:#p1" : 여러 파라미터 조합
     */
    String key();

    /**
     * 락 획득 대기 시간 (기본값: 5초)
     *
     * 이 시간 동안 락을 시도하며, 시간 초과 시 false 반환
     */
    long waitTime() default 5;

    /**
     * 락 유지 시간 (기본값: 2초)
     *
     * 락을 획득한 후 이 시간 동안 락을 유지합니다.
     * 명시적으로 해제하거나 이 시간이 경과하면 자동 해제됩니다.
     */
    long leaseTime() default 2;

    /**
     * 시간 단위 (기본값: TimeUnit.SECONDS)
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
