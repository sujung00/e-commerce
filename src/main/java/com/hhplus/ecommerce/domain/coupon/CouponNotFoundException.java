package com.hhplus.ecommerce.domain.coupon;

/**
 * CouponNotFoundException
 * 쿠폰을 찾을 수 없을 때 발생하는 예외
 * API 명세: COUPON_NOT_FOUND, 404 Not Found
 */
public class CouponNotFoundException extends RuntimeException {
    private static final String MESSAGE = "쿠폰을 찾을 수 없습니다";
    private final Long couponId;

    public CouponNotFoundException(Long couponId) {
        super(MESSAGE);
        this.couponId = couponId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public String getErrorCode() {
        return "COUPON_NOT_FOUND";
    }
}
