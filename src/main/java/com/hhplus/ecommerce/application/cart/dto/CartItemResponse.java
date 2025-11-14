package com.hhplus.ecommerce.application.cart.dto;

import com.hhplus.ecommerce.domain.cart.CartItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장바구니 항목 응답 (Application layer 내부 DTO)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {
    private Long cartItemId;
    private Long productId;
    private String productName;
    private Long optionId;
    private String optionName;
    private Integer quantity;
    private Long unitPrice;
    private Long subtotal;

    public static CartItemResponse from(CartItem item, String productName, String optionName) {
        return CartItemResponse.builder()
                .cartItemId(item.getCartItemId())
                .productId(item.getProductId())
                .productName(productName)
                .optionId(item.getOptionId())
                .optionName(optionName)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .build();
    }
}