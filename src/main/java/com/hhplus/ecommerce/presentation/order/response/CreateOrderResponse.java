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
 * 주문 생성 응답 DTO (Presentation layer)
 *
 * 책임:
 * - HTTP API 응답 직렬화 (@JsonProperty, @JsonFormat)
 * - Application layer와 독립적 (변환은 OrderMapper에서 처리)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResponse {
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

    /**
     * Domain Order 엔티티에서 Presentation DTO로 변환
     *
     * @param order Domain Order 엔티티
     * @return Presentation layer CreateOrderResponse
     */
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
                        .map(item -> OrderItemResponse.builder()
                                .orderItemId(item.getOrderItemId())
                                .productId(item.getProductId())
                                .productName(item.getProductName())
                                .optionId(item.getOptionId())
                                .optionName(item.getOptionName())
                                .quantity(item.getQuantity())
                                .price(item.getUnitPrice())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(order.getCreatedAt())
                .build();
    }
}
