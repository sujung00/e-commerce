package com.hhplus.ecommerce.domain.order;

/**
 * 주문 소유자가 일치하지 않을 때 발생하는 예외 (404 Not Found)
 * 권한 없는 사용자가 다른 사용자의 주문에 접근하려는 경우
 */
public class UserMismatchException extends RuntimeException {
    public static final String ERROR_CODE = "USER_MISMATCH";

    public UserMismatchException(Long orderId, Long userId) {
        super("주문 사용자 불일치. Order ID: " + orderId + ", User ID: " + userId);
    }

    public UserMismatchException(String message) {
        super(message);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
