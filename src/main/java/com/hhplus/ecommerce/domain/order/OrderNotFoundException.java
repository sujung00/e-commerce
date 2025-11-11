package com.hhplus.ecommerce.domain.order;

/**
 * 주문을 찾을 수 없을 때 발생하는 예외
 */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long orderId) {
        super("주문을 찾을 수 없습니다. Order ID: " + orderId);
    }

    public OrderNotFoundException(String message) {
        super(message);
    }
}
