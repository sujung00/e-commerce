package com.hhplus.ecommerce.presentation.coupon.response;

import lombok.*;

import java.util.List;

/**
 * GetUserCouponsResponse - 사용자 쿠폰 목록 조회 응답 DTO
 * API: GET /coupons/issued (200 OK)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetUserCouponsResponse {
    /**
     * 사용자가 보유한 쿠폰 목록
     */
    private List<UserCouponResponse> userCoupons;
}
