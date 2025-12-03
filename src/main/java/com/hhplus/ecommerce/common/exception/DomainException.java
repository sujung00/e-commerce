package com.hhplus.ecommerce.common.exception;

/**
 * DomainException - 도메인 규칙 위반 예외
 *
 * 역할:
 * - 비즈니스 도메인의 규칙 위반 시 발생
 * - 유효성 검증, 상태 값 오류 등
 * - 일반적으로 클라이언트 오류(4XX)로 응답
 *
 * 사용 예:
 * - UserNotFoundException: 사용자 조회 실패
 * - InsufficientBalanceException: 잔액 부족
 * - InvalidOrderStatusException: 잘못된 주문 상태
 */
public class DomainException extends BizException {

    public DomainException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DomainException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public DomainException(ErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }
}
