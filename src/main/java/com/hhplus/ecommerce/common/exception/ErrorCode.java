package com.hhplus.ecommerce.common.exception;

/**
 * ErrorCode - 비즈니스 예외 코드 정의
 *
 * 역할:
 * - 모든 비즈니스 예외의 코드와 메시지 정의
 * - HTTP 상태 코드 매핑
 * - 일관된 에러 응답 제공
 *
 * 코드 형식: {LAYER}_{DOMAIN}_{ERROR}
 * 예: DOMAIN_USER_NOT_FOUND, APP_ORDER_CREATION_FAILED
 */
public enum ErrorCode {

    // ========== Domain Layer Errors (4XX) ==========

    // User Domain
    USER_NOT_FOUND("DOMAIN_USER_NOT_FOUND", "사용자를 찾을 수 없습니다", 404),
    INSUFFICIENT_BALANCE("DOMAIN_USER_INSUFFICIENT_BALANCE", "잔액이 부족합니다", 400),
    INVALID_BALANCE("DOMAIN_USER_INVALID_BALANCE", "유효하지 않은 잔액입니다", 400),

    // Product Domain
    PRODUCT_NOT_FOUND("DOMAIN_PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다", 404),
    INSUFFICIENT_STOCK("DOMAIN_PRODUCT_INSUFFICIENT_STOCK", "재고가 부족합니다", 400),
    INVALID_QUANTITY("DOMAIN_PRODUCT_INVALID_QUANTITY", "유효하지 않은 수량입니다", 400),

    // Coupon Domain
    COUPON_NOT_FOUND("DOMAIN_COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다", 404),
    COUPON_INACTIVE("DOMAIN_COUPON_INACTIVE", "비활성화된 쿠폰입니다", 400),
    COUPON_EXPIRED("DOMAIN_COUPON_EXPIRED", "유효기간이 만료된 쿠폰입니다", 400),
    COUPON_OUT_OF_STOCK("DOMAIN_COUPON_OUT_OF_STOCK", "쿠폰이 모두 소진되었습니다", 400),
    COUPON_ALREADY_ISSUED("DOMAIN_COUPON_ALREADY_ISSUED", "이미 발급받은 쿠폰입니다", 400),

    // Order Domain
    ORDER_NOT_FOUND("DOMAIN_ORDER_NOT_FOUND", "주문을 찾을 수 없습니다", 404),
    INVALID_ORDER_STATUS("DOMAIN_ORDER_INVALID_STATUS", "유효하지 않은 주문 상태입니다", 400),
    USER_MISMATCH("DOMAIN_ORDER_USER_MISMATCH", "주문 사용자가 일치하지 않습니다", 403),

    // Cart Domain
    CART_ITEM_NOT_FOUND("DOMAIN_CART_ITEM_NOT_FOUND", "장바구니 항목을 찾을 수 없습니다", 404),
    CART_INVALID_QUANTITY("DOMAIN_CART_INVALID_QUANTITY", "수량은 1 이상 1000 이하여야 합니다", 400),

    // ========== Application Layer Errors (5XX) ==========

    // Order Application
    ORDER_CREATION_FAILED("APP_ORDER_CREATION_FAILED", "주문 생성에 실패했습니다", 500),
    ORDER_CANCELLATION_FAILED("APP_ORDER_CANCELLATION_FAILED", "주문 취소에 실패했습니다", 500),
    ORDER_PAYMENT_FAILED("APP_ORDER_PAYMENT_FAILED", "결제 처리에 실패했습니다", 500),
    CRITICAL_COMPENSATION_FAILURE("APP_CRITICAL_COMPENSATION_FAILURE", "중요 보상 트랜잭션에 실패했습니다 - 즉시 수동 개입 필요", 500),
    COMPENSATION_FAILED("APP_COMPENSATION_FAILED", "보상 트랜잭션 처리에 실패했습니다", 500),

    // Coupon Application
    COUPON_ISSUANCE_FAILED("APP_COUPON_ISSUANCE_FAILED", "쿠폰 발급에 실패했습니다", 500),

    // ========== System Errors (5XX) ==========

    DATABASE_ERROR("SYSTEM_DATABASE_ERROR", "데이터베이스 오류가 발생했습니다", 500),
    CACHE_ERROR("SYSTEM_CACHE_ERROR", "캐시 오류가 발생했습니다", 500),
    LOCK_ACQUISITION_FAILED("SYSTEM_LOCK_ACQUISITION_FAILED", "분산락 획득에 실패했습니다", 500),
    EXTERNAL_API_ERROR("SYSTEM_EXTERNAL_API_ERROR", "외부 API 호출에 실패했습니다", 503),
    INTERNAL_SERVER_ERROR("SYSTEM_INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다", 500);

    private final String code;
    private final String message;
    private final int statusCode;

    ErrorCode(String code, String message, int statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
