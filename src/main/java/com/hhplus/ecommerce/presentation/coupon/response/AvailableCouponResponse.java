package com.hhplus.ecommerce.presentation.coupon.response;

import com.hhplus.ecommerce.domain.Coupon;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AvailableCouponResponse - 발급 가능한 쿠폰 정보 응답 DTO
 * API: GET /coupons (200 OK)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableCouponResponse {
    /**
     * 쿠폰 ID
     */
    private Long couponId;

    /**
     * 쿠폰명
     */
    private String couponName;

    /**
     * 쿠폰 설명
     */
    private String description;

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
     * 유효 시작 시각
     */
    private LocalDateTime validFrom;

    /**
     * 유효 종료 시각
     */
    private LocalDateTime validUntil;

    /**
     * 남은 발급 가능 수량
     */
    private Integer remainingQty;

    /**
     * Coupon 객체에서 Response로 변환
     */
    public static AvailableCouponResponse from(Coupon coupon) {
        return AvailableCouponResponse.builder()
                .couponId(coupon.getCouponId())
                .couponName(coupon.getCouponName())
                .description(coupon.getDescription())
                .discountType(coupon.getDiscountType())
                .discountAmount(coupon.getDiscountAmount())
                .discountRate(coupon.getDiscountRate())
                .validFrom(coupon.getValidFrom())
                .validUntil(coupon.getValidUntil())
                .remainingQty(coupon.getRemainingQty())
                .build();
    }
}
