package com.hhplus.ecommerce.domain.coupon;

import lombok.*;

import java.time.LocalDateTime;

/**
 * UserCoupon 도메인 엔티티
 * 사용자가 발급받은 쿠폰 기록
 * status: ACTIVE | USED | EXPIRED
 * UNIQUE(user_id, coupon_id)로 중복 발급 방지
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCoupon {
    private Long userCouponId;
    private Long userId;
    private Long couponId;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime usedAt;
    private Long orderId;
}