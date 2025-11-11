package com.hhplus.ecommerce.presentation.order.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("order_item_id")
    private Long orderItemId;

    @JsonProperty("product_id")
    private Long productId;

    @JsonProperty("product_name")
    private String productName;

    @JsonProperty("option_id")
    private Long optionId;

    @JsonProperty("option_name")
    private String optionName;

    private Integer quantity;

    @JsonProperty("unit_price")
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
