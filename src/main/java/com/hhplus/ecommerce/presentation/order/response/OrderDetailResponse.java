package com.hhplus.ecommerce.presentation.order.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("order_id")
    private Long orderId;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("order_status")
    private String orderStatus;

    private Long subtotal;

    @JsonProperty("coupon_discount")
    private Long couponDiscount;

    @JsonProperty("coupon_id")
    private Long couponId;

    @JsonProperty("final_amount")
    private Long finalAmount;

    @JsonProperty("order_items")
    private List<OrderItemResponse> orderItems;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @JsonProperty("created_at")
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
