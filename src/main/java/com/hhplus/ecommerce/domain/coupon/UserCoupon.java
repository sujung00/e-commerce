package com.hhplus.ecommerce.domain.coupon;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * UserCoupon 도메인 엔티티
 * 사용자가 발급받은 쿠폰 기록
 * status: UNUSED | USED | EXPIRED | CANCELLED (Enum)
 * UNIQUE(user_id, coupon_id)로 중복 발급 방지
 *
 * 동시성 제어:
 * - @Version: 낙관적 락 (미래 확장 포인트)
 * - 현재는 비관적 락(SELECT ... FOR UPDATE) 사용
 * - 향후 성능 개선 시 낙관적 락으로 전환 가능
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

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;
}