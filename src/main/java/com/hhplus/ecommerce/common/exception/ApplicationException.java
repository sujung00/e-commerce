package com.hhplus.ecommerce.common.exception;

/**
 * ApplicationException - 애플리케이션 계층 비즈니스 로직 실패 예외
 *
 * 역할:
 * - 비즈니스 로직 실행 중 발생하는 실패 상황
 * - 주로 서비스 계층에서 발생
 * - 일반적으로 서버 오류(5XX)로 응답
 *
 * 사용 예:
 * - OrderCreationFailedException: 주문 생성 실패
 * - PaymentFailedException: 결제 처리 실패
 * - CouponIssuanceFailedException: 쿠폰 발급 실패
 *
 * DomainException과의 차이:
 * - DomainException: 도메인 규칙 자체의 위반 (예: 잔액 부족)
 * - ApplicationException: 규칙은 만족하지만 프로세스 실패 (예: 외부 API 호출 실패)
 */
public class ApplicationException extends BizException {

    public ApplicationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ApplicationException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public ApplicationException(ErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }
}
