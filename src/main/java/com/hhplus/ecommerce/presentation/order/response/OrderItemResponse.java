package com.hhplus.ecommerce.presentation.order.response;

import com.hhplus.ecommerce.domain.order.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 항목 응답 DTO
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
    private Long unitPrice;
    private Long subtotal;

    public static OrderItemResponse fromOrderItem(OrderItem orderItem) {
        return OrderItemResponse.builder()
                .orderItemId(orderItem.getOrderItemId())
                .productId(orderItem.getProductId())
                .productName(orderItem.getProductName())
                .optionId(orderItem.getOptionId())
                .optionName(orderItem.getOptionName())
                .quantity(orderItem.getQuantity())
                .unitPrice(orderItem.getUnitPrice())
                .subtotal(orderItem.getSubtotal())
                .build();
    }
}
