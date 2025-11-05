package com.hhplus.ecommerce.common.exception;

/**
 * 상품을 찾을 수 없을 때 발생하는 예외
 */
public class ProductNotFoundException extends RuntimeException {
    private final String errorCode = "PRODUCT_NOT_FOUND";

    public ProductNotFoundException(String message) {
        super(message);
    }

    public String getErrorCode() {
        return errorCode;
    }
}