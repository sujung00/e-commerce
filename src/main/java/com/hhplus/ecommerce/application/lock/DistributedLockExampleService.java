package com.hhplus.ecommerce.application.lock;

import com.hhplus.ecommerce.infrastructure.lock.DistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 분산락 사용 예제 Service
 *
 * Redis 분산락을 실제로 사용하는 방법을 보여줍니다.
 * 각 메서드는 @DistributedLock 어노테이션으로 보호됩니다.
 */
@Service
@Slf4j
public class DistributedLockExampleService {

    /**
     * 고정 키를 사용하는 분산락 예제
     *
     * 같은 coupon:1 키로 접근하는 모든 요청이 직렬화됩니다.
     */
    @DistributedLock(key = "coupon:1", waitTime = 5, leaseTime = 2)
    public boolean issueCouponFixed() {
        log.info("[DistributedLock] 고정 키 쿠폰 발급 - coupon:1");
        try {
            // 비즈니스 로직 실행
            Thread.sleep(1000); // 1초 작업
            log.info("[DistributedLock] 쿠폰 발급 완료");
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[DistributedLock] 쿠폰 발급 중 인터럽트", e);
            return false;
        }
    }

    /**
     * 동적 키를 사용하는 분산락 예제
     *
     * couponId를 파라미터로 받아 동적으로 키를 생성합니다.
     * 예: issueCoupon(5) → 락 키: "coupon:5"
     */
    @DistributedLock(key = "coupon:#p0", waitTime = 5, leaseTime = 2)
    public boolean issueCoupon(Long couponId) {
        log.info("[DistributedLock] 동적 키 쿠폰 발급 - couponId: {}", couponId);
        try {
            // 실제로는 데이터베이스 업데이트 등의 작업이 들어갑니다.
            Thread.sleep(1000);
            log.info("[DistributedLock] 쿠폰 발급 완료 - couponId: {}", couponId);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[DistributedLock] 쿠폰 발급 중 인터럽트 - couponId: {}", couponId, e);
            return false;
        }
    }

    /**
     * 여러 파라미터를 사용하는 분산락 예제
     *
     * 사용자 ID와 쿠폰 ID를 조합하여 키를 생성합니다.
     * 예: issueCouponToUser(10, 5) → 락 키: "user:10:coupon:5"
     */
    @DistributedLock(key = "user:#p0:coupon:#p1", waitTime = 5, leaseTime = 2)
    public boolean issueCouponToUser(Long userId, Long couponId) {
        log.info("[DistributedLock] 사용자별 쿠폰 발급 - userId: {}, couponId: {}", userId, couponId);
        try {
            // 사용자별 쿠폰 중복 발급 방지
            Thread.sleep(1500);
            log.info("[DistributedLock] 사용자 쿠폰 발급 완료 - userId: {}, couponId: {}", userId, couponId);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[DistributedLock] 사용자 쿠폰 발급 중 인터럽트 - userId: {}, couponId: {}", userId, couponId, e);
            return false;
        }
    }

    /**
     * 주문 생성 분산락 예제
     *
     * 동일한 상품에 대한 동시 주문을 직렬화합니다.
     * 예: createOrder(100) → 락 키: "product:stock:100"
     */
    @DistributedLock(key = "product:stock:#p0", waitTime = 10, leaseTime = 3)
    public boolean createOrder(Long productId) {
        log.info("[DistributedLock] 주문 생성 - productId: {}", productId);
        try {
            // 재고 차감 로직
            Thread.sleep(2000);
            log.info("[DistributedLock] 주문 생성 완료 - productId: {}", productId);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[DistributedLock] 주문 생성 중 인터럽트 - productId: {}", productId, e);
            return false;
        }
    }

    /**
     * 결제 처리 분산락 예제
     *
     * 사용자 잔액에 대한 동시 결제를 직렬화합니다.
     * 예: payment(50) → 락 키: "user:balance:50"
     */
    @DistributedLock(key = "user:balance:#p0", waitTime = 5, leaseTime = 2)
    public boolean payment(Long userId) {
        log.info("[DistributedLock] 결제 처리 - userId: {}", userId);
        try {
            // 잔액 확인 및 차감
            Thread.sleep(1500);
            log.info("[DistributedLock] 결제 완료 - userId: {}", userId);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[DistributedLock] 결제 처리 중 인터럽트 - userId: {}", userId, e);
            return false;
        }
    }
}
