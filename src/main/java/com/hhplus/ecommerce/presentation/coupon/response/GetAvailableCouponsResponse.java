package com.hhplus.ecommerce.presentation.coupon.response;

import lombok.*;

import java.util.List;

/**
 * GetAvailableCouponsResponse - 사용 가능한 쿠폰 목록 조회 응답 DTO
 * API: GET /coupons (200 OK)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetAvailableCouponsResponse {
    /**
     * 발급 가능한 쿠폰 목록
     */
    private List<AvailableCouponResponse> coupons;
}
