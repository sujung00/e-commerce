package com.hhplus.ecommerce.infrastructure.constants;

/**
 * LockConstants - 분산락 설정 상수
 *
 * 역할:
 * - Redisson 기반 분산락의 타임아웃 설정 관리
 * - @DistributedLock 애노테이션과 동적 락 호출에서 공통으로 사용
 * - 락 획득 대기 시간과 리스 시간을 일관되게 관리
 *
 * 사용 예:
 * - @DistributedLock(waitTime = LockConstants.DEFAULT_LOCK_WAIT_TIME, ...)
 * - lockService.lock("key", LockConstants.DEFAULT_LOCK_WAIT_TIME, LockConstants.DEFAULT_LOCK_LEASE_TIME)
 */
public class LockConstants {

    // ========== Distributed Lock Timeout Constants ==========

    /** 분산락 획득 대기 시간 (초) */
    public static final long DEFAULT_LOCK_WAIT_TIME = 5L;

    /** 분산락 리스 시간 (초) - 락을 자동으로 해제할 때까지의 시간 */
    public static final long DEFAULT_LOCK_LEASE_TIME = 2L;

    // ========== Lock Operation Timeout Constants ==========

    /** 사용자 잔액 락 대기 시간 (초) */
    public static final long USER_BALANCE_LOCK_WAIT_TIME = 5L;

    /** 사용자 잔액 락 리스 시간 (초) */
    public static final long USER_BALANCE_LOCK_LEASE_TIME = 2L;

    /** 쿠폰 재고 락 대기 시간 (초) */
    public static final long COUPON_STOCK_LOCK_WAIT_TIME = 5L;

    /** 쿠폰 재고 락 리스 시간 (초) */
    public static final long COUPON_STOCK_LOCK_LEASE_TIME = 2L;

    /** 상품 재고 락 대기 시간 (초) */
    public static final long PRODUCT_STOCK_LOCK_WAIT_TIME = 5L;

    /** 상품 재고 락 리스 시간 (초) */
    public static final long PRODUCT_STOCK_LOCK_LEASE_TIME = 2L;

    // ========== Lock Key Templates ==========

    /** 사용자 잔액 락 키 템플릿: "user:balance:{userId}" */
    public static final String USER_BALANCE_LOCK_KEY_TEMPLATE = "user:balance:%d";

    /** 쿠폰 재고 락 키 템플릿: "coupon:stock:{couponId}" */
    public static final String COUPON_STOCK_LOCK_KEY_TEMPLATE = "coupon:stock:%d";

    /** 상품 재고 락 키 템플릿: "product:stock:{productId}:{optionId}" */
    public static final String PRODUCT_STOCK_LOCK_KEY_TEMPLATE = "product:stock:%d:%d";

    // ========== Lock Timeout Explanation ==========
    /*
     * Wait Time vs Lease Time:
     *
     * waitTime = 5초
     * - 락 획득 시도 시 최대 5초 동안 대기
     * - 5초 내에 락을 획득하지 못하면 LockException 발생
     * - 현재 락을 보유한 다른 스레드가 리스시간 내에 락을 해제할 때까지 대기
     *
     * leaseTime = 2초
     * - 락을 획득한 후 2초 동안 락을 보유
     * - 2초 후 자동으로 락이 해제됨 (deadlock 방지)
     * - try-finally에서 unlock()을 명시적으로 호출하면 대기 없이 즉시 해제됨
     *
     * 추천값:
     * - waitTime >= leaseTime (대기 시간이 리스 시간보다 길어야 함)
     * - waitTime: 5초 (일반적인 동시성 제어)
     * - leaseTime: 2초 (작업 완료 여유 시간)
     */

    private LockConstants() {
        throw new AssertionError("LockConstants는 인스턴스화할 수 없습니다");
    }
}
