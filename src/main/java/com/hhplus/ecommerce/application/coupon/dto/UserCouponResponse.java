package com.hhplus.ecommerce.application.coupon.dto;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사용자 쿠폰 응답 (Application layer 내부 DTO)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCouponResponse {
    private Long userCouponId;
    private Long userId;
    private Long couponId;
    private String couponName;
    private String discountType;
    private Long discountAmount;
    private BigDecimal discountRate;

    // ✅ 수정: String → Enum (외부 반환 시 .name()으로 String 변환)
    private String status;

    private LocalDateTime issuedAt;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;

    public static UserCouponResponse from(UserCoupon userCoupon, Coupon coupon) {
        return UserCouponResponse.builder()
                .userCouponId(userCoupon.getUserCouponId())
                .userId(userCoupon.getUserId())
                .couponId(userCoupon.getCouponId())
                .couponName(coupon.getCouponName())
                .discountType(coupon.getDiscountType())
                .discountAmount(coupon.getDiscountAmount())
                .discountRate(coupon.getDiscountRate())
                // ✅ 수정: Enum을 String으로 변환
                .status(userCoupon.getStatus().name())
                .issuedAt(userCoupon.getIssuedAt())
                .validFrom(coupon.getValidFrom())
                .validUntil(coupon.getValidUntil())
                .build();
    }
}
