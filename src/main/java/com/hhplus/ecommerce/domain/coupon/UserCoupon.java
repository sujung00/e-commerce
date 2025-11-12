package com.hhplus.ecommerce.domain.coupon;

import lombok.*;

import java.time.LocalDateTime;

/**
 * UserCoupon 도메인 엔티티
 * 사용자가 발급받은 쿠폰 기록
 * status: UNUSED | USED | EXPIRED | CANCELLED (Enum)
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

    // ✅ 수정: String → UserCouponStatus Enum
    @Builder.Default
    private UserCouponStatus status = UserCouponStatus.UNUSED;  // 기본값: UNUSED

    private LocalDateTime issuedAt;
    private LocalDateTime usedAt;
    private Long orderId;
}