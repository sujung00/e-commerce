package com.hhplus.ecommerce.presentation.cart.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장바구니 아이템 추가 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddCartItemRequest {

    @JsonProperty("product_id")
    private Long productId;

    @JsonProperty("option_id")
    private Long optionId;

    private Integer quantity;
}
