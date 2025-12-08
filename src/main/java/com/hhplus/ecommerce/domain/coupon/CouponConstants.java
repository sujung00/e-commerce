package com.hhplus.ecommerce.domain.coupon;

/**
 * CouponConstants - 쿠폰 도메인 상수
 *
 * 역할:
 * - 쿠폰 할인금, 할인율, 재고 관련 검증 규칙 관리
 * - 쿠폰 발급, 사용, 상태 변경 등의 비즈니스 규칙 상수화
 *
 * 사용 예:
 * - if (coupon.getDiscountAmount() < CouponConstants.MIN_DISCOUNT_AMOUNT) throw ...
 * - if (coupon.getRemainingQuantity() <= CouponConstants.ZERO_REMAINING_QTY) throw ...
 */
public class CouponConstants {

    // ========== Coupon Discount Amount Constants ==========

    /** 쿠폰 할인금 기준값 (0원) */
    public static final long ZERO_DISCOUNT_AMOUNT = 0L;

    /** 쿠폰 할인금 최소값 (0원 초과) */
    public static final long MIN_DISCOUNT_AMOUNT = 0L;

    // ========== Coupon Discount Rate Constants ==========

    /** 쿠폰 할인율 최소값 (0% 포함) */
    public static final double MIN_DISCOUNT_RATE = 0.0;

    /** 쿠폰 할인율 최대값 (100% 포함) */
    public static final double MAX_DISCOUNT_RATE = 1.0;

    // ========== Coupon Stock Constants ==========

    /** 쿠폰 재고 기준값 (0개) */
    public static final int ZERO_REMAINING_QTY = 0;

    /** 쿠폰 재고 최소값 (0개 초과) */
    public static final int MIN_COUPON_QUANTITY = 0;

    // ========== Coupon Status Constants ==========

    /** 쿠폰 상태: 활성화 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 쿠폰 상태: 비활성화 */
    public static final String STATUS_INACTIVE = "INACTIVE";

    /** 쿠폰 상태: 종료됨 (유효기간 만료) */
    public static final String STATUS_EXPIRED = "EXPIRED";

    /** 쿠폰 초기 상태 */
    public static final String INITIAL_COUPON_STATUS = STATUS_ACTIVE;

    // ========== Coupon Validation Messages ==========

    public static final String MSG_INVALID_DISCOUNT_AMOUNT = "쿠폰 할인금은 0원 초과여야 합니다";
    public static final String MSG_INVALID_DISCOUNT_RATE = "쿠폰 할인율은 0~100% 범위여야 합니다";
    public static final String MSG_INSUFFICIENT_COUPON_STOCK = "쿠폰 재고가 부족합니다";
    public static final String MSG_COUPON_ALREADY_ISSUED = "이미 발급받은 쿠폰입니다";

    private CouponConstants() {
        throw new AssertionError("CouponConstants는 인스턴스화할 수 없습니다");
    }
}
