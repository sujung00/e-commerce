package com.hhplus.ecommerce.domain.order;

/**
 * 주문 처리 중 발생하는 일반적인 예외
 *
 * 사용 시나리오:
 * - 쿠폰 처리 실패
 * - 자식 트랜잭션 예외로 인한 부모 롤백
 * - 주문 처리 중 데이터 불일치
 */
public class OrderException extends RuntimeException {
    public OrderException(String message) {
        super(message);
    }

    public OrderException(String message, Throwable cause) {
        super(message, cause);
    }
}
