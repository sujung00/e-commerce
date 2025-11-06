package com.hhplus.ecommerce.presentation.coupon.response;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * UserCouponResponse - 사용자 쿠폰 정보 응답 DTO
 * API: GET /coupons/issued (200 OK)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCouponResponse {
    /**
     * 사용자 쿠폰 발급 ID
     */
    private Long userCouponId;

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
     * 사용 시각 (status=USED일 때만 설정)
     */
    private LocalDateTime usedAt;

    /**
     * 유효 시작 시각
     */
    private LocalDateTime validFrom;

    /**
     * 유효 종료 시각
     */
    private LocalDateTime validUntil;

    /**
     * UserCoupon + Coupon 객체에서 Response로 변환
     */
    public static UserCouponResponse from(UserCoupon userCoupon, Coupon coupon) {
        return UserCouponResponse.builder()
                .userCouponId(userCoupon.getUserCouponId())
                .couponId(userCoupon.getCouponId())
                .couponName(coupon.getCouponName())
                .discountType(coupon.getDiscountType())
                .discountAmount(coupon.getDiscountAmount())
                .discountRate(coupon.getDiscountRate())
                .status(userCoupon.getStatus())
                .issuedAt(userCoupon.getIssuedAt())
                .usedAt(userCoupon.getUsedAt())
                .validFrom(coupon.getValidFrom())
                .validUntil(coupon.getValidUntil())
                .build();
    }
}
