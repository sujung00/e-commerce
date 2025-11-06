package com.hhplus.ecommerce.domain.order;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Order 도메인 엔티티
 * 주문 정보 및 주문 항목 관리
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private Long orderId;
    private Long userId;
    private String orderStatus;  // COMPLETED, PENDING, FAILED
    private Long couponId;
    private Long couponDiscount;
    private Long subtotal;
    private Long finalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    /**
     * 주문 생성 팩토리 메서드
     */
    public static Order createOrder(Long userId, Long couponId, Long couponDiscount, Long subtotal, Long finalAmount) {
        return Order.builder()
                .userId(userId)
                .couponId(couponId)
                .couponDiscount(couponDiscount)
                .subtotal(subtotal)
                .finalAmount(finalAmount)
                .orderStatus("COMPLETED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 주문 항목 추가
     */
    public void addOrderItem(OrderItem orderItem) {
        this.orderItems.add(orderItem);
    }
}
