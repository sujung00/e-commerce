package com.hhplus.ecommerce.application.order.dto;

import com.hhplus.ecommerce.domain.order.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 항목 응답 (Application layer 내부 DTO)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private Long orderItemId;
    private Long productId;
    private String productName;
    private Long optionId;
    private String optionName;
    private Integer quantity;
    private Long price;

    public static OrderItemResponse fromOrderItem(OrderItem orderItem) {
        return OrderItemResponse.builder()
                .orderItemId(orderItem.getOrderItemId())
                .productId(orderItem.getProductId())
                .productName(orderItem.getProductName())
                .optionId(orderItem.getOptionId())
                .optionName(orderItem.getOptionName())
                .quantity(orderItem.getQuantity())
                .price(orderItem.getPrice())
                .build();
    }
}