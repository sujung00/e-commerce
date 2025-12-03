package com.hhplus.ecommerce.common.exception;

/**
 * BizException - 비즈니스 예외의 최상위 클래스
 *
 * 역할:
 * - 모든 비즈니스 예외의 기본 클래스
 * - 에러 코드와 메시지 정보 포함
 * - 예외 계층의 최상위
 *
 * 예외 계층:
 * BizException (최상위)
 * ├─ DomainException (도메인 규칙 위반)
 * ├─ ApplicationException (비즈니스 로직 실패)
 * └─ SystemException (시스템 오류)
 */
public abstract class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String detailMessage) {
        super(errorCode.getMessage() + " | " + detailMessage);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getStatusCode() {
        return errorCode.getStatusCode();
    }

    public String getErrorCodeValue() {
        return errorCode.getCode();
    }
}
