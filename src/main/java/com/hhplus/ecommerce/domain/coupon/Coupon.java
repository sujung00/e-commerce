package com.hhplus.ecommerce.domain.coupon;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Coupon 도메인 엔티티
 * 할인 쿠폰 정보 및 발급 수량 관리
 * discount_type: FIXED_AMOUNT | PERCENTAGE
 * version은 낙관적 락(쿠폰 발급 시 동시성 제어)
 * remaining_qty는 원자적으로 감소되어야 함
 */
@Entity
@Table(name = "coupons")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "coupon_name", nullable = false)
    private String couponName;

    @Column(name = "description")
    private String description;

    @Column(name = "discount_type", nullable = false)
    private String discountType;

    @Column(name = "discount_amount", nullable = false)
    @Builder.Default
    private Long discountAmount = 0L;

    @Column(name = "discount_rate", nullable = false)
    @Builder.Default
    private BigDecimal discountRate = BigDecimal.ZERO;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "remaining_qty", nullable = false)
    private Integer remainingQty;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * ✅ 추가: 쿠폰 할인액 검증
     * - FIXED_AMOUNT: discountAmount > 0
     * - PERCENTAGE: 0 <= discountRate <= 1.0
     */
    public void validateDiscount() {
        if ("FIXED_AMOUNT".equals(discountType)) {
            if (discountAmount == null || discountAmount <= 0) {
                throw new IllegalArgumentException("정액 할인액은 0보다 커야 합니다");
            }
        } else if ("PERCENTAGE".equals(discountType)) {
            if (discountRate == null ||
                discountRate.compareTo(BigDecimal.ZERO) < 0 ||
                discountRate.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("할인율은 0.0~1.0 범위여야 합니다");
            }
        }
    }

    /**
     * 쿠폰 재고 차감 (Domain 비즈니스 로직)
     *
     * 동시성 제어:
     * - @Version으로 낙관적 락 보호
     * - Service 계층의 비관적 락(SELECT ... FOR UPDATE)과 함께 사용
     * - DB 레벨 비관적 락이 메인 전략, @Version은 추가 안전장치
     *
     * 비즈니스 규칙:
     * - 재고가 0보다 커야 함 (재고 부족 시 예외)
     * - 차감 후 version 자동 증가 (JPA 관리)
     * - 남은 수량이 0이면 is_active를 false로 변경
     *
     * @throws IllegalArgumentException 재고 부족
     */
    public void decreaseStock() {
        if (this.remainingQty <= 0) {
            throw new IllegalArgumentException("쿠폰 재고가 부족합니다 (남은 수량: " + this.remainingQty + ")");
        }
        this.remainingQty--;
        this.updatedAt = LocalDateTime.now();

        // 재고가 소진되면 자동으로 비활성화
        if (this.remainingQty == 0) {
            this.isActive = false;
        }
    }

    /**
     * 쿠폰 재고 확인
     */
    public boolean hasStock() {
        return this.remainingQty > 0;
    }

    /**
     * 쿠폰 활성화 상태 확인
     */
    public boolean isActiveCoupon() {
        return Boolean.TRUE.equals(this.isActive);
    }

    /**
     * 유효 기간 확인
     */
    public boolean isValidPeriod(LocalDateTime now) {
        return !now.isBefore(this.validFrom) && !now.isAfter(this.validUntil);
    }
}