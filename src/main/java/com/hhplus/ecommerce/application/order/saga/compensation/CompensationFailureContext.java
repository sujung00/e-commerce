package com.hhplus.ecommerce.application.order.saga.compensation;

import com.hhplus.ecommerce.application.order.saga.context.SagaContext;
import com.hhplus.ecommerce.application.order.saga.orchestration.SagaStep;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * CompensationFailureContext - 보상 실패 컨텍스트 DTO
 *
 * 역할:
 * - 보상 실패 시 필요한 모든 정보를 캡슐화
 * - OrderSagaOrchestrator가 내부 상태를 직접 노출하지 않고 DTO로 전달
 * - SagaCompensationHandler가 보상 실패를 처리하는 데 필요한 정보 제공
 *
 * 설계 원칙:
 * - Orchestrator의 내부 구현을 숨김 (Encapsulation)
 * - Handler는 이 DTO만 의존하도록 설계 (Loose Coupling)
 * - 불변 객체로 설계 (Immutable)
 *
 * 포함 정보:
 * - orderId: 주문 ID (nullable - 주문 생성 전 실패 시 null)
 * - userId: 사용자 ID
 * - stepName: 실패한 Step 이름
 * - stepOrder: Step 실행 순서
 * - error: 발생한 예외
 * - sagaContext: Saga 실행 컨텍스트 (FailedCompensation 생성에 필요)
 * - failedAt: 실패 시각
 */
@Getter
@Builder
public class CompensationFailureContext {

    /**
     * 주문 ID (nullable)
     * - 주문 생성 전 실패 시 null
     */
    private final Long orderId;

    /**
     * 사용자 ID
     */
    private final Long userId;

    /**
     * 실패한 Step 이름 (예: "DeductInventoryStep")
     */
    private final String stepName;

    /**
     * Step 실행 순서 (1~4)
     */
    private final Integer stepOrder;

    /**
     * 발생한 예외
     */
    private final Exception error;

    /**
     * Saga 실행 컨텍스트
     * - FailedCompensation 생성에 필요
     * - Handler가 추가 정보를 참조할 수 있도록 제공
     */
    private final SagaContext sagaContext;

    /**
     * 실패 시각
     */
    @Builder.Default
    private final LocalDateTime failedAt = LocalDateTime.now();

    /**
     * CompensationFailureContext 생성 팩토리 메서드
     *
     * @param orderId 주문 ID (nullable)
     * @param userId 사용자 ID
     * @param step 실패한 Step
     * @param error 발생한 예외
     * @param context Saga 실행 컨텍스트
     * @return CompensationFailureContext 객체
     */
    public static CompensationFailureContext from(Long orderId,
                                                   Long userId,
                                                   SagaStep step,
                                                   Exception error,
                                                   SagaContext context) {
        return CompensationFailureContext.builder()
                .orderId(orderId)
                .userId(userId)
                .stepName(step.getName())
                .stepOrder(step.getOrder())
                .error(error)
                .sagaContext(context)
                .failedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 예외가 Critical인지 확인
     *
     * @return Critical 여부
     */
    public boolean isCriticalError() {
        return error instanceof com.hhplus.ecommerce.common.exception.CriticalException;
    }

    /**
     * 에러 메시지 반환
     *
     * @return 에러 메시지
     */
    public String getErrorMessage() {
        return error != null ? error.getMessage() : "Unknown error";
    }
}