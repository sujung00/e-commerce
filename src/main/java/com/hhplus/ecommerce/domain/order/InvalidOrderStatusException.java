package com.hhplus.ecommerce.domain.order;

/**
 * 주문 상태가 유효하지 않을 때 발생하는 예외 (400 Bad Request)
 * 취소 불가능한 상태: CANCELLED, SHIPPED, DELIVERED 등
 */
public class InvalidOrderStatusException extends RuntimeException {
    public static final String ERROR_CODE = "INVALID_ORDER_STATUS";

    public InvalidOrderStatusException(String message) {
        super(message);
    }

    public InvalidOrderStatusException(Long orderId, String currentStatus) {
        super("취소 불가능한 주문 상태입니다. Status: " + currentStatus);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
