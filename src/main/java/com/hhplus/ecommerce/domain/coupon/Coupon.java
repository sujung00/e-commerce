package com.hhplus.ecommerce.domain.coupon;

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
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon {
    private Long couponId;
    private String couponName;
    private String description;
    private String discountType;

    // ✅ 수정: nullable 제거, 기본값 설정
    @Builder.Default
    private Long discountAmount = 0L;  // FIXED_AMOUNT 타입일 때만 사용

    @Builder.Default
    private BigDecimal discountRate = BigDecimal.ZERO;  // PERCENTAGE 타입일 때만 사용

    private Integer totalQuantity;
    private Integer remainingQty;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;

    // ✅ 수정: nullable 제거, 기본값 설정
    @Builder.Default
    private Boolean isActive = true;  // 기본값: true (활성)

    @Builder.Default
    private Long version = 1L;  // 기본값: 1 (낙관적 락)

    private LocalDateTime createdAt;
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
}