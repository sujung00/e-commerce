package com.hhplus.ecommerce.application.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 쿠폰 발급 커맨드 (Application layer 내부 DTO)
 * Presentation layer의 IssueCouponRequest와 독립적
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueCouponCommand {
    private Long couponId;
}
