package com.hhplus.ecommerce.presentation.coupon.response;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * IssueCouponResponse - 쿠폰 발급 응답 DTO
 * API: POST /coupons/issue (201 Created)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueCouponResponse {
    /**
     * 사용자 쿠폰 발급 ID
     */
    private Long userCouponId;

    /**
     * 사용자 ID
     */
    private Long userId;

    /**
     * 쿠폰 ID
     */
    private Long couponId;

    /**
     * 쿠폰명
     */
    private String couponName;

    /**
     * 할인 타입 (FIXED_AMOUNT | PERCENTAGE)
     */
    private String discountType;

    /**
     * 할인액 (discount_type=FIXED_AMOUNT일 때)
     */
    private Long discountAmount;

    /**
     * 할인율 (discount_type=PERCENTAGE일 때)
     */
    private BigDecimal discountRate;

    /**
     * 쿠폰 상태 (ACTIVE | USED | EXPIRED)
     */
    private String status;

    /**
     * 발급 시각
     */
    private LocalDateTime issuedAt;

    /**
     * 유효 시작 시각
     */
    private LocalDateTime validFrom;

    /**
     * 유효 종료 시각
     */
    private LocalDateTime validUntil;

    /**
     * UserCoupon 객체에서 Response로 변환
     */
    public static IssueCouponResponse from(UserCoupon userCoupon, Coupon coupon) {
        return IssueCouponResponse.builder()
                .userCouponId(userCoupon.getUserCouponId())
                .userId(userCoupon.getUserId())
                .couponId(userCoupon.getCouponId())
                .couponName(coupon.getCouponName())
                .discountType(coupon.getDiscountType())
                .discountAmount(coupon.getDiscountAmount())
                .discountRate(coupon.getDiscountRate())
                .status(userCoupon.getStatus().name())
                .issuedAt(userCoupon.getIssuedAt())
                .validFrom(coupon.getValidFrom())
                .validUntil(coupon.getValidUntil())
                .build();
    }
}
