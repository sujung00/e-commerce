package com.hhplus.ecommerce.domain.cart;

/**
 * 유효하지 않은 수량일 때 발생하는 예외
 */
public class InvalidQuantityException extends RuntimeException {
    private static final String MESSAGE = "수량은 1 이상 1000 이하여야 합니다";

    public InvalidQuantityException(Integer quantity) {
        super(String.format("%s (입력값: %d)", MESSAGE, quantity));
    }

    public InvalidQuantityException() {
        super(MESSAGE);
    }
}
