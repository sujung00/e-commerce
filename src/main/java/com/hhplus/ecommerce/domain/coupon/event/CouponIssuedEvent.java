package com.hhplus.ecommerce.domain.coupon.event;

import lombok.Getter;
import lombok.ToString;

/**
 * 쿠폰 발급 완료 이벤트
 *
 * 용도: 쿠폰 발급 후 캐시 무효화를 트랜잭션 외부에서 처리
 * 발행 시점: CouponService.issueCouponWithLockInternal() 트랜잭션 성공 후
 * 리스너: CouponEventListener
 */
@Getter
@ToString
public class CouponIssuedEvent {

    private final Long couponId;
    private final Long userId;
    private final Integer remainingQtyBefore;
    private final Integer remainingQtyAfter;
    private final boolean isStockExhausted;
    private final boolean isLastItem;

    public CouponIssuedEvent(Long couponId,
                            Long userId,
                            Integer remainingQtyBefore,
                            Integer remainingQtyAfter) {
        this.couponId = couponId;
        this.userId = userId;
        this.remainingQtyBefore = remainingQtyBefore;
        this.remainingQtyAfter = remainingQtyAfter;
        this.isStockExhausted = remainingQtyAfter == 0;
        this.isLastItem = remainingQtyBefore == 1;
    }
}