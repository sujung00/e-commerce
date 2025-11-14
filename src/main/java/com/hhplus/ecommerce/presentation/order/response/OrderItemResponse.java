package com.hhplus.ecommerce.presentation.order.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 항목 응답 DTO (Presentation layer)
 *
 * 책임:
 * - HTTP API 응답 직렬화 (@JsonProperty)
 * - Application layer와 독립적 (변환은 OrderMapper에서 처리)
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

    @JsonProperty("price")
    private Long price;
}
