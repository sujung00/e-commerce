package com.hhplus.ecommerce.domain;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Order 도메인 엔티티
 * 주문 정보 및 결제 상태 관리
 * final_amount = subtotal - coupon_discount
 * order_status: COMPLETED | PENDING | FAILED
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private Long orderId;
    private Long userId;
    private String orderStatus;
    private Long couponId;
    private Long couponDiscount;
    private Long subtotal;
    private Long finalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}