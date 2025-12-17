package com.hhplus.ecommerce.domain.order.event;

/**
 * Saga 이벤트 타입
 *
 * 역할: Order Saga 실행 결과를 표현하는 이벤트 타입
 *
 * 타입:
 * - COMPLETED: Saga 정상 완료 (재고 차감 → 포인트 차감 → 쿠폰 사용 → 주문 생성 성공)
 * - FAILED: Saga 실행 중 실패 (보상 트랜잭션은 성공적으로 완료됨)
 * - COMPENSATION_FAILED: 보상 트랜잭션 실패 (수동 개입 필요)
 *
 * 통합된 기존 이벤트:
 * - PaymentSuccessEvent → COMPLETED
 * - CompensationCompletedEvent → (제거, FAILED로 통합)
 * - CompensationFailedEvent → COMPENSATION_FAILED
 *
 * 설계 원칙:
 * - 이벤트는 "무슨 일이 일어났는가"만 표현
 * - "어떻게 처리할지"는 리스너의 책임
 * - enum 확장으로 새로운 이벤트 타입 추가 가능
 */
public enum SagaEventType {

    /**
     * Saga 정상 완료
     *
     * 의미: 모든 Step이 성공적으로 실행되어 주문이 생성됨
     * 발행 시점: OrderSagaOrchestrator.executeSaga() 성공 직후
     * 처리: 결제 성공 알림 발송 (이메일, SMS, Slack 등)
     */
    COMPLETED,

    /**
     * Saga 실행 중 실패 (보상 성공)
     *
     * 의미: 특정 Step에서 실패했지만, 보상 트랜잭션은 성공적으로 완료됨
     * 발행 시점: OrderSagaOrchestrator.executeSaga() 예외 발생 후 보상 완료
     * 처리: 실패 원인 로깅, 모니터링 (알림은 선택적)
     */
    FAILED,

    /**
     * 보상 트랜잭션 실패 (Critical)
     *
     * 의미: 보상 처리 중 오류가 발생하여 수동 개입이 필요한 상태
     * 발행 시점: OrderSagaService.compensateOrder() 예외 발생 시
     * 처리: 긴급 알림 발송, 수동 복구 작업 트리거
     */
    COMPENSATION_FAILED
}