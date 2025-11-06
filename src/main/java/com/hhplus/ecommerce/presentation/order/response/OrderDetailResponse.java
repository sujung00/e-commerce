package com.hhplus.ecommerce.presentation.order.response;

import com.hhplus.ecommerce.domain.order.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 주문 상세 조회 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailResponse {
    private Long orderId;
    private Long userId;
    private String orderStatus;
    private Long subtotal;
    private Long couponDiscount;
    private Long couponId;
    private Long finalAmount;
    private List<OrderItemResponse> orderItems;
    private LocalDateTime createdAt;

    public static OrderDetailResponse fromOrder(Order order) {
        return OrderDetailResponse.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .orderStatus(order.getOrderStatus())
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
