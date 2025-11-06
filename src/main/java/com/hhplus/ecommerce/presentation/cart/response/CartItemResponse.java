package com.hhplus.ecommerce.presentation.cart.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hhplus.ecommerce.domain.cart.CartItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 장바구니 아이템 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {

    @JsonProperty("cart_item_id")
    private Long cartItemId;

    @JsonProperty("cart_id")
    private Long cartId;

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

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    /**
     * CartItem 엔티티에서 DTO로 변환 (productName, optionName은 별도로 제공)
     */
    public static CartItemResponse from(CartItem cartItem, String productName, String optionName) {
        return CartItemResponse.builder()
                .cartItemId(cartItem.getCartItemId())
                .cartId(cartItem.getCartId())
                .productId(cartItem.getProductId())
                .productName(productName)
                .optionId(cartItem.getOptionId())
                .optionName(optionName)
                .quantity(cartItem.getQuantity())
                .unitPrice(cartItem.getUnitPrice())
                .subtotal(cartItem.getSubtotal())
                .createdAt(cartItem.getCreatedAt())
                .updatedAt(cartItem.getUpdatedAt())
                .build();
    }
}
