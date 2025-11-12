package com.hhplus.ecommerce.application.coupon.dto;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 쿠폰 발급 응답 (Application layer 내부 DTO)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueCouponResponse {
    private Long userCouponId;
    private Long userId;
    private Long couponId;
    private String couponName;
    private String discountType;
    private Long discountAmount;
    private BigDecimal discountRate;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;

    public static IssueCouponResponse from(UserCoupon userCoupon, Coupon coupon) {
        return IssueCouponResponse.builder()
                .userCouponId(userCoupon.getUserCouponId())
                .userId(userCoupon.getUserId())
                .couponId(userCoupon.getCouponId())
                .couponName(coupon.getCouponName())
                .discountType(coupon.getDiscountType())
                .discountAmount(coupon.getDiscountAmount())
                .discountRate(coupon.getDiscountRate())
                .status(userCoupon.getStatus())
                .issuedAt(userCoupon.getIssuedAt())
                .validFrom(coupon.getValidFrom())
                .validUntil(coupon.getValidUntil())
                .build();
    }
}
