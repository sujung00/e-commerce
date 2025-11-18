package com.hhplus.ecommerce.application.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 사용 가능한 쿠폰 목록 응답 (Application layer 내부 DTO)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetAvailableCouponsResponse {
    private List<AvailableCouponResponse> coupons;
}
