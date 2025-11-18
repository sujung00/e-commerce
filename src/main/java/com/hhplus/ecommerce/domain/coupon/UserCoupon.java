package com.hhplus.ecommerce.domain.coupon;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * UserCoupon 도메인 엔티티
 * 사용자가 발급받은 쿠폰 기록
 * status: UNUSED | USED | EXPIRED | CANCELLED (Enum)
 * UNIQUE(user_id, coupon_id)로 중복 발급 방지
 */
@Entity
@Table(name = "user_coupons", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "coupon_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCoupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_coupon_id")
    private Long userCouponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserCouponStatus status = UserCouponStatus.UNUSED;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;
}