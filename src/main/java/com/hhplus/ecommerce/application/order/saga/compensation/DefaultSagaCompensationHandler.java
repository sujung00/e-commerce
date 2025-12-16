package com.hhplus.ecommerce.application.order.saga.compensation;

import com.hhplus.ecommerce.application.alert.AlertService;
import com.hhplus.ecommerce.application.order.saga.CompensationDLQ;
import com.hhplus.ecommerce.application.order.saga.FailedCompensation;
import com.hhplus.ecommerce.application.order.saga.SagaStep;
import com.hhplus.ecommerce.common.exception.CompensationException;
import com.hhplus.ecommerce.common.exception.CriticalException;
import com.hhplus.ecommerce.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DefaultSagaCompensationHandler - 기본 보상 실패 처리 구현체
 *
 * 역할:
 * - OrderSagaOrchestrator로부터 분리된 보상 실패 처리 로직
 * - Critical 여부 판단 및 적절한 처리 수행
 * - AlertService 알림, CompensationDLQ 발행 담당
 *
 * 처리 전략:
 * 1. Critical Exception:
 *    - AlertService로 즉시 알림 발송
 *    - FailedCompensation DLQ 발행
 *    - CompensationException throw (상위로 전파)
 *
 * 2. 일반 Exception:
 *    - FailedCompensation DLQ 발행
 *    - 예외를 전파하지 않음 (Best Effort)
 *    - 다음 보상을 계속 진행할 수 있도록 함
 *
 * 의존성:
 * - AlertService: 관리자 알림 발송
 * - CompensationDLQ: 실패한 보상 메시지를 DLQ로 발행
 */
@Component
public class DefaultSagaCompensationHandler implements SagaCompensationHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultSagaCompensationHandler.class);

    private final AlertService alertService;
    private final CompensationDLQ compensationDLQ;

    public DefaultSagaCompensationHandler(AlertService alertService,
                                         CompensationDLQ compensationDLQ) {
        this.alertService = alertService;
        this.compensationDLQ = compensationDLQ;
    }

    /**
     * 보상 실패 처리
     *
     * @param context 보상 실패 컨텍스트
     * @throws CompensationException Critical 보상 실패 시
     */
    @Override
    public void handleFailure(CompensationFailureContext context) {
        Exception error = context.getError();

        // ========== Critical Exception 처리 ==========
        if (error instanceof CriticalException) {
            handleCriticalFailure(context, (CriticalException) error);
        }
        // ========== 일반 Exception 처리 ==========
        else {
            handleGeneralFailure(context);
        }
    }

    /**
     * Critical 보상 실패 처리
     *
     * 처리 내용:
     * 1. 에러 로깅 (Critical 표시)
     * 2. AlertService로 즉시 알림 발송
     * 3. FailedCompensation DLQ 발행
     * 4. CompensationException throw (상위로 전파)
     *
     * @param context 보상 실패 컨텍스트
     * @param criticalError Critical 예외
     * @throws CompensationException Critical 보상 실패
     */
    private void handleCriticalFailure(CompensationFailureContext context,
                                      CriticalException criticalError) {
        Long orderId = context.getOrderId();
        String stepName = context.getStepName();

        // ========== Step 1: Critical 에러 로깅 ==========
        log.error("[DefaultSagaCompensationHandler] 🚨 중요 보상 실패 (Critical) - " +
                        "Step={}, orderId={}, error={}",
                stepName, orderId, criticalError.getMessage(), criticalError);

        // ========== Step 2: AlertService로 즉시 알림 발송 ==========
        try {
            alertService.notifyCriticalCompensationFailure(orderId, stepName);
            log.info("[DefaultSagaCompensationHandler] Critical 알림 발송 완료 - orderId={}, step={}",
                    orderId, stepName);
        } catch (Exception alertError) {
            // 알림 실패는 로깅만 하고 계속 진행
            log.error("[DefaultSagaCompensationHandler] Critical 알림 발송 실패 (무시됨) - error={}",
                    alertError.getMessage());
        }

        // ========== Step 3: FailedCompensation DLQ 발행 ==========
        publishToDLQ(context);

        // ========== Step 4: CompensationException throw (상위로 전파) ==========
        log.error("[DefaultSagaCompensationHandler] ⚠️ Critical 보상 실패로 인해 보상 프로세스 중단 - " +
                        "orderId={}, stepName={}",
                orderId, stepName);

        throw new CompensationException(
                ErrorCode.CRITICAL_COMPENSATION_FAILURE,
                "Critical compensation failed",
                stepName,
                orderId,
                criticalError
        );
    }

    /**
     * 일반 보상 실패 처리 (Best Effort)
     *
     * 처리 내용:
     * 1. 에러 로깅
     * 2. FailedCompensation DLQ 발행
     * 3. 예외를 전파하지 않음 (다음 보상 계속 진행)
     *
     * @param context 보상 실패 컨텍스트
     */
    private void handleGeneralFailure(CompensationFailureContext context) {
        String stepName = context.getStepName();
        Exception error = context.getError();

        // ========== Step 1: 일반 에러 로깅 ==========
        log.error("[DefaultSagaCompensationHandler] 보상 실패 (무시하고 계속) - " +
                        "Step={}, error={}",
                stepName, error.getMessage(), error);

        // ========== Step 2: FailedCompensation DLQ 발행 ==========
        publishToDLQ(context);

        // ========== Step 3: Best Effort - 계속 진행 ==========
        log.warn("[DefaultSagaCompensationHandler] 일반 보상 실패는 무시하고 다음 Step 보상 계속 진행 - " +
                        "Step={}",
                stepName);
    }

    /**
     * FailedCompensation을 DLQ로 발행
     *
     * @param context 보상 실패 컨텍스트
     */
    private void publishToDLQ(CompensationFailureContext context) {
        try {
            // FailedCompensation 생성
            // Note: FailedCompensation.from()은 SagaStep 객체를 받지만,
            // context는 stepName만 가지고 있으므로 임시 Step 객체를 생성하거나
            // FailedCompensation.builder()를 직접 사용해야 함
            FailedCompensation failedCompensation = FailedCompensation.builder()
                    .orderId(context.getOrderId())
                    .userId(context.getUserId())
                    .stepName(context.getStepName())
                    .stepOrder(context.getStepOrder())
                    .errorMessage(context.getErrorMessage())
                    .stackTrace(getStackTraceAsString(context.getError()))
                    .contextSnapshot(context.getSagaContext().toString())
                    .build();

            // DLQ 발행
            compensationDLQ.publish(failedCompensation);

            log.info("[DefaultSagaCompensationHandler] FailedCompensation DLQ 발행 완료 - " +
                            "orderId={}, step={}",
                    context.getOrderId(), context.getStepName());

        } catch (Exception dlqError) {
            // DLQ 발행 실패는 로깅만 하고 계속 진행
            log.error("[DefaultSagaCompensationHandler] DLQ 발행 실패 (무시됨) - error={}",
                    dlqError.getMessage(), dlqError);
        }
    }

    /**
     * 스택 트레이스를 String으로 변환
     *
     * @param error 예외 객체
     * @return 스택 트레이스 문자열
     */
    private String getStackTraceAsString(Exception error) {
        if (error == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(error.getClass().getName()).append(": ").append(error.getMessage()).append("\n");

        for (StackTraceElement element : error.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
            // 스택 트레이스 길이 제한 (너무 길면 로그/DB 부담)
            if (sb.length() > 2000) {
                sb.append("\t... (truncated)");
                break;
            }
        }

        return sb.toString();
    }
}