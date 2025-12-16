package com.hhplus.ecommerce.application.order.saga.compensation;

/**
 * SagaCompensationHandler - 보상 실패 처리 전담 인터페이스
 *
 * 역할:
 * - Saga 보상 트랜잭션 실패 시 처리 전략을 정의
 * - OrderSagaOrchestrator로부터 보상 실패 처리 책임을 분리
 * - Critical 여부 판단, 알림 발송, DLQ 발행 등을 담당
 *
 * 설계 원칙:
 * - 단일 책임 원칙(SRP): 보상 실패 처리만 담당
 * - 전략 패턴(Strategy Pattern): 다양한 보상 실패 전략 구현 가능
 * - 의존성 역전(DIP): Orchestrator는 인터페이스에만 의존
 *
 * 구현체 예시:
 * - DefaultSagaCompensationHandler: 기본 보상 실패 처리 (AlertService, DLQ)
 * - AsyncSagaCompensationHandler: 비동기 보상 실패 처리
 * - RetryingSagaCompensationHandler: 재시도 기능 포함 보상 실패 처리
 *
 * 처리 흐름:
 * 1. CompensationFailureContext 수신
 * 2. Critical 여부 판단 (CriticalException인지 확인)
 * 3. Critical: AlertService 알림 + DLQ 발행 + 예외 전파
 * 4. 일반: DLQ 발행만 (Best Effort)
 */
public interface SagaCompensationHandler {

    /**
     * 보상 실패 처리
     *
     * 역할:
     * - 보상 실패 시 적절한 처리 수행
     * - Critical 여부에 따라 다른 처리 전략 적용
     *
     * Critical Exception:
     * - AlertService로 즉시 알림 발송
     * - CompensationDLQ에 실패 메시지 발행
     * - CompensationException을 throw하여 상위로 전파
     *
     * 일반 Exception:
     * - CompensationDLQ에 실패 메시지 발행
     * - 예외를 전파하지 않음 (Best Effort)
     *
     * @param context 보상 실패 컨텍스트
     * @throws com.hhplus.ecommerce.common.exception.CompensationException Critical 보상 실패 시
     */
    void handleFailure(CompensationFailureContext context);
}