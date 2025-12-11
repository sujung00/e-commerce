package com.hhplus.ecommerce.common.exception;

/**
 * CriticalException - 중요 보상 트랜잭션 실패 예외
 *
 * 역할:
 * - Saga 보상 트랜잭션 중 중요(critical) 단계 실패 시 발생
 * - AlertService를 통한 즉시 알림 트리거
 * - 수동 개입 필요성을 나타내는 마커 예외
 *
 * 발생 시나리오:
 * - 재고 복구 실패 (데이터베이스 락 타임아웃)
 * - 잔액 복구 실패 (동시성 충돌)
 * - 쿠폰 복구 실패 (정합성 오류)
 *
 * 처리 방법:
 * - OrderSagaOrchestrator가 catch하여 AlertService 호출
 * - CompensationDLQ로 발행하여 수동 재처리 가능하게 함
 * - CompensationException으로 wrapping하여 상위로 전파
 *
 * 예시:
 * <pre>
 * try {
 *     productRepository.restoreStock(optionId, quantity);
 * } catch (DataAccessException e) {
 *     throw new CriticalException(ErrorCode.CRITICAL_COMPENSATION_FAILURE,
 *         "재고 복구 실패: optionId=" + optionId, e);
 * }
 * </pre>
 */
public class CriticalException extends BizException {

    public CriticalException(ErrorCode errorCode) {
        super(errorCode);
    }

    public CriticalException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public CriticalException(ErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }

    public CriticalException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(errorCode, detailMessage + " | Cause: " + cause.getMessage());
    }
}