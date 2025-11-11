package com.hhplus.ecommerce.domain.cart;

/**
 * 장바구니 아이템을 찾을 수 없을 때 발생하는 예외
 */
public class CartItemNotFoundException extends RuntimeException {
    private static final String MESSAGE = "장바구니 아이템을 찾을 수 없습니다";

    public CartItemNotFoundException(Long cartItemId) {
        super(String.format("%s (ID: %d)", MESSAGE, cartItemId));
    }

    public CartItemNotFoundException() {
        super(MESSAGE);
    }
}