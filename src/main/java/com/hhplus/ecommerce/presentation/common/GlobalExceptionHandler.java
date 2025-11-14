package com.hhplus.ecommerce.presentation.common;

import com.hhplus.ecommerce.domain.cart.CartItemNotFoundException;
import com.hhplus.ecommerce.domain.cart.InvalidQuantityException;
import com.hhplus.ecommerce.domain.coupon.CouponNotFoundException;
import com.hhplus.ecommerce.domain.order.OrderNotFoundException;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.domain.product.ProductNotFoundException;
import com.hhplus.ecommerce.presentation.common.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * GlobalExceptionHandler - 전역 예외 처리 (Presentation 계층)
 *
 * 역할:
 * - 모든 계층에서 발생하는 예외를 잡아서 통일된 에러 응답으로 변환
 * - API 명세(api-specification.md)의 에러 응답 형식을 준수
 *
 * 에러 응답 형식:
 * {
 *   "error_code": "ERR-001",
 *   "error_message": "메시지",
 *   "timestamp": "2025-11-07T12:34:56Z",
 *   "request_id": "req-abc123def456"
 * }
 *
 * HTTP 상태 코드 매핑:
 * - 404 Not Found: 리소스를 찾을 수 없음 (ProductNotFoundException, UserNotFoundException 등)
 * - 400 Bad Request: 파라미터 검증 실패 또는 비즈니스 로직 실패 (IllegalArgumentException, InvalidQuantityException 등)
 * - 500 Internal Server Error: 서버 내부 오류
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 상품을 찾을 수 없는 경우 (404)
     *
     * API 명세:
     * - Error Code: PRODUCT_NOT_FOUND
     * - HTTP Status: 404 Not Found
     * - Message: "상품을 찾을 수 없습니다"
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFoundException(ProductNotFoundException e) {
        ErrorResponse errorResponse = ErrorResponse.of(e.getErrorCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * 사용자를 찾을 수 없는 경우 (404)
     *
     * API 명세:
     * - Error Code: USER_NOT_FOUND
     * - HTTP Status: 404 Not Found
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFoundException(UserNotFoundException e) {
        ErrorResponse errorResponse = ErrorResponse.of("USER_NOT_FOUND", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * 주문을 찾을 수 없는 경우 (404)
     *
     * API 명세:
     * - Error Code: ORDER_NOT_FOUND
     * - HTTP Status: 404 Not Found
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFoundException(OrderNotFoundException e) {
        ErrorResponse errorResponse = ErrorResponse.of("ORDER_NOT_FOUND", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * 장바구니 아이템을 찾을 수 없는 경우 (404)
     *
     * API 명세:
     * - Error Code: CART_ITEM_NOT_FOUND
     * - HTTP Status: 404 Not Found
     */
    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCartItemNotFoundException(CartItemNotFoundException e) {
        ErrorResponse errorResponse = ErrorResponse.of("CART_ITEM_NOT_FOUND", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * 쿠폰을 찾을 수 없는 경우 (404)
     *
     * API 명세:
     * - Error Code: COUPON_NOT_FOUND
     * - HTTP Status: 404 Not Found
     */
    @ExceptionHandler(CouponNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCouponNotFoundException(CouponNotFoundException e) {
        ErrorResponse errorResponse = ErrorResponse.of(e.getErrorCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * 유효하지 않은 수량 (400)
     *
     * API 명세:
     * - Error Code: INVALID_REQUEST
     * - HTTP Status: 400 Bad Request
     */
    @ExceptionHandler(InvalidQuantityException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQuantityException(InvalidQuantityException e) {
        ErrorResponse errorResponse = ErrorResponse.of("INVALID_REQUEST", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * 잘못된 요청 파라미터 또는 비즈니스 로직 실패 (400)
     *
     * API 명세:
     * - Error Code: INVALID_REQUEST (또는 구체적인 ERR-001, ERR-002 등)
     * - HTTP Status: 400 Bad Request
     * - 메시지 예시:
     *   - ERR-001: "[옵션명]의 재고가 부족합니다"
     *   - ERR-002: "잔액이 부족합니다"
     *   - ERR-003: "유효하지 않은 쿠폰입니다"
     *   - INVALID_PRODUCT_OPTION: "상품과 옵션이 일치하지 않습니다"
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        ErrorResponse errorResponse = ErrorResponse.of("INVALID_REQUEST", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * 동시성 제어 실패 (409)
     *
     * API 명세:
     * - Error Code: CONFLICT
     * - HTTP Status: 409 Conflict
     * - Message: "충돌이 발생했습니다"
     * - 상황: 낙관적 락 버전 불일치 (ERR-004)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException e) {
        String errorCode = e.getMessage().contains("ERR-004") ? "ERR-004" : "CONFLICT";
        ErrorResponse errorResponse = ErrorResponse.of(errorCode, e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * 지원하지 않는 작업 (400)
     *
     * API 명세:
     * - Error Code: INVALID_REQUEST
     * - HTTP Status: 400 Bad Request
     * - 상황: 필수 헤더 누락 (X-USER-ID) 등 클라이언트 요청 오류
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedOperationException(UnsupportedOperationException e) {
        ErrorResponse errorResponse = ErrorResponse.of("INVALID_REQUEST", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * 서버 내부 오류 (500)
     *
     * API 명세:
     * - Error Code: INTERNAL_SERVER_ERROR
     * - HTTP Status: 500 Internal Server Error
     * - Message: "서버 오류가 발생했습니다"
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        logger.error("Unhandled exception occurred: ", e);
        ErrorResponse errorResponse = ErrorResponse.of("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
