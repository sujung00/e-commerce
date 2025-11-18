package com.hhplus.ecommerce.application.cart.dto;

import com.hhplus.ecommerce.domain.cart.Cart;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 장바구니 응답 (Application layer 내부 DTO)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponseDto {
    private Long cartId;
    private Long userId;
    private Integer totalItems;
    private Long totalPrice;
    private List<CartItemResponse> items;
    private LocalDateTime updatedAt;

    public static CartResponseDto fromCart(Cart cart, List<CartItemResponse> items) {
        return CartResponseDto.builder()
                .cartId(cart.getCartId())
                .userId(cart.getUserId())
                .totalItems(cart.getTotalItems())
                .totalPrice(cart.getTotalPrice())
                .items(items)
                .updatedAt(cart.getUpdatedAt())
                .build();
    }
}