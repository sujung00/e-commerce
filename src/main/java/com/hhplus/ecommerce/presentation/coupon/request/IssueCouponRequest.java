package com.hhplus.ecommerce.presentation.coupon.request;

import lombok.*;

/**
 * IssueCouponRequest - 쿠폰 발급 요청 DTO
 * API: POST /coupons/issue
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueCouponRequest {
    /**
     * 발급받을 쿠폰 ID
     */
    private Long couponId;
}
