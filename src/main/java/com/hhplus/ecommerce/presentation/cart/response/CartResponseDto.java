package com.hhplus.ecommerce.presentation.cart.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 장바구니 조회 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponseDto {

    @JsonProperty("cart_id")
    private Long cartId;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("total_items")
    private Integer totalItems;

    @JsonProperty("total_price")
    private Long totalPrice;

    private List<CartItemResponse> items;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
