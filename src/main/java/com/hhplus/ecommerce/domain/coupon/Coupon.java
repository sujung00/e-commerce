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
    private Long discountAmount;
    private BigDecimal discountRate;
    private Integer totalQuantity;
    private Integer remainingQty;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private Boolean isActive;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}