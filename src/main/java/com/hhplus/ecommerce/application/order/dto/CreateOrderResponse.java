package com.hhplus.ecommerce.application.order.dto;

import com.hhplus.ecommerce.domain.order.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 주문 생성 응답 (Application layer 내부 DTO)
 * Domain의 Order 엔티티로부터 변환
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResponse {
    private Long orderId;
    private Long userId;
    private String orderStatus;
    private Long subtotal;
    private Long couponDiscount;
    private Long couponId;
    private Long finalAmount;
    private List<OrderItemResponse> orderItems;
    private LocalDateTime createdAt;

    public static CreateOrderResponse fromOrder(Order order) {
        return CreateOrderResponse.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .orderStatus(order.getOrderStatus().name())
                .subtotal(order.getSubtotal())
                .couponDiscount(order.getCouponDiscount())
                .couponId(order.getCouponId())
                .finalAmount(order.getFinalAmount())
                .orderItems(order.getOrderItems().stream()
                        .map(OrderItemResponse::fromOrderItem)
                        .collect(Collectors.toList()))
                .createdAt(order.getCreatedAt())
                .build();
    }
}