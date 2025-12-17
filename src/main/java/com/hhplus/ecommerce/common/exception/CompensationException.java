package com.hhplus.ecommerce.common.exception;

/**
 * CompensationException - 보상 트랜잭션 실패 예외
 *
 * 역할:
 * - Saga 보상 트랜잭션 실패를 상위로 전파하는 예외
 * - CriticalException을 wrapping하여 더 구체적인 컨텍스트 제공
 * - 최종적으로 OrderSagaService가 catch하여 예외 응답 반환
 *
 * 발생 시나리오:
 * - OrderSagaOrchestrator.compensate() 중 CriticalException 발생
 * - 여러 Step 중 하나라도 critical 보상 실패
 * - AlertService 알림 발송 후 throw
 *
 * 처리 방법:
 * - OrderSagaService가 최종 catch
 * - 사용자에게 "주문 처리 중 오류 발생, 고객센터 문의" 응답
 * - 관리자는 AlertService 알림으로 즉시 파악
 *
 * 예시:
 * <pre>
 * catch (CriticalException e) {
 *     alertService.notifyCriticalCompensationFailure(orderId, stepName);
 *     throw new CompensationException(
 *         ErrorCode.CRITICAL_COMPENSATION_FAILURE,
 *         "Critical compensation failed for step: " + stepName, e);
 * }
 * </pre>
 */
public class CompensationException extends BizException {

    private final String stepName;
    private final Long orderId;

    public CompensationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message);
        initCause(cause);
        this.stepName = null;
        this.orderId = null;
    }

    public CompensationException(ErrorCode errorCode, String message, String stepName, Long orderId, Throwable cause) {
        super(errorCode, message + " [Step: " + stepName + ", OrderId: " + orderId + "]");
        initCause(cause);
        this.stepName = stepName;
        this.orderId = orderId;
    }

    public String getStepName() {
        return stepName;
    }

    public Long getOrderId() {
        return orderId;
    }
}