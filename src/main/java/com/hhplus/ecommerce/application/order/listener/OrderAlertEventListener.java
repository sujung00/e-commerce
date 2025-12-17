package com.hhplus.ecommerce.application.order.listener;

import com.hhplus.ecommerce.application.alert.AlertService;
import com.hhplus.ecommerce.domain.order.event.OrderSagaEvent;
import com.hhplus.ecommerce.domain.order.event.SagaEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 Saga 이벤트 리스너 (Phase 3 통합)
 *
 * 역할: Order Saga 실행 결과 이벤트를 수신하여 알림 처리
 *
 * Phase 3 변경사항:
 * - 기존 3개 리스너 통합 → 단일 리스너로 통합
 * - PaymentSuccessEvent, CompensationCompletedEvent, CompensationFailedEvent 제거
 * - OrderSagaEvent 하나로 모든 Saga 결과 처리
 * - SagaEventType 기준으로 분기 처리
 *
 * 통합 전:
 * - handlePaymentSuccess(PaymentSuccessEvent)
 * - handleCompensationCompleted(CompensationCompletedEvent)
 * - handleCompensationFailed(CompensationFailedEvent)
 *
 * 통합 후:
 * - handleOrderSagaEvent(OrderSagaEvent) - SagaEventType 기준 분기
 *
 * 트랜잭션 분리 이유:
 * - 알림 발송은 외부 I/O 작업 (이메일, SMS, Slack 등)
 * - 트랜잭션 내부에서 실행 시 성능 저하 및 트랜잭션 지연
 * - 알림 실패가 비즈니스 트랜잭션에 영향을 주지 않아야 함
 *
 * 이벤트 처리 시점: AFTER_COMMIT
 * - 트랜잭션 커밋 성공 후에만 실행
 * - 롤백 시 이벤트 미발행으로 불필요한 알림 방지
 *
 * 비동기 처리:
 * - @Async로 별도 스레드에서 실행
 * - 메인 비즈니스 로직 블로킹 방지
 * - 알림 실패 시에도 try-catch로 처리하여 시스템 안정성 보장
 */
@Component
public class OrderAlertEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderAlertEventListener.class);

    private final AlertService alertService;

    public OrderAlertEventListener(AlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * Order Saga 이벤트 리스너 (Phase 3 통합)
     *
     * 트랜잭션 커밋 후 비동기로 Saga 결과에 따른 알림 발송
     *
     * 처리 플로우:
     * 1. SagaEventType 확인
     * 2. COMPLETED → 결제 성공 알림
     * 3. FAILED → Saga 실패 로깅 (알림 선택적)
     * 4. COMPENSATION_FAILED → 긴급 알림 (수동 개입 필요)
     *
     * 실패 처리:
     * - 알림 실패는 로깅만 하고 예외를 전파하지 않음
     * - 비즈니스 트랜잭션은 이미 성공했으므로 알림은 선택적 기능
     *
     * @param event Order Saga 통합 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderSagaEvent(OrderSagaEvent event) {
        log.info("[OrderAlertEventListener] OrderSagaEvent 수신 - type={}, orderId={}, userId={}",
                event.getSagaEventType(), event.getOrderId(), event.getUserId());

        // SagaEventType 기준으로 분기 처리
        switch (event.getSagaEventType()) {
            case COMPLETED:
                handleSagaCompleted(event);
                break;

            case FAILED:
                handleSagaFailed(event);
                break;

            case COMPENSATION_FAILED:
                handleCompensationFailed(event);
                break;

            default:
                log.warn("[OrderAlertEventListener] 알 수 없는 SagaEventType: {}", event.getSagaEventType());
                break;
        }
    }

    /**
     * Saga 성공 처리 (COMPLETED)
     *
     * 처리 내용:
     * - 결제 성공 알림 발송 (이메일, SMS, Slack 등)
     * - 사용자에게 주문 완료 안내
     *
     * @param event OrderSagaEvent (COMPLETED)
     */
    private void handleSagaCompleted(OrderSagaEvent event) {
        log.info("[OrderAlertEventListener] Saga 성공 처리 - orderId={}, userId={}, amount={}",
                event.getOrderId(), event.getUserId(), event.getFinalAmount());

        try {
            alertService.notifyPaymentSuccess(
                    event.getOrderId(),
                    event.getUserId(),
                    event.getFinalAmount()
            );
            log.info("[OrderAlertEventListener] 결제 성공 알림 발송 완료 - orderId={}", event.getOrderId());

        } catch (Exception e) {
            // 알림 실패는 로깅만 하고 예외를 전파하지 않음
            log.error("[OrderAlertEventListener] 결제 성공 알림 발송 실패 (무시됨) - orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Saga 실패 처리 (FAILED)
     *
     * 처리 내용:
     * - Saga 실행 중 실패했지만 보상은 성공한 상태
     * - 실패 원인 로깅
     * - 필요 시 사용자에게 주문 실패 안내 (선택적)
     *
     * @param event OrderSagaEvent (FAILED)
     */
    private void handleSagaFailed(OrderSagaEvent event) {
        log.info("[OrderAlertEventListener] Saga 실패 처리 (보상 성공) - orderId={}, userId={}, error={}",
                event.getOrderId(), event.getUserId(), event.getErrorMessage());

        try {
            // 선택적: 사용자에게 주문 실패 알림
            // alertService.notifySagaFailed(event.getOrderId(), event.getUserId(), event.getErrorMessage());

            // 현재는 로깅만 수행 (별도 알림 불필요)
            log.info("[OrderAlertEventListener] Saga 실패 로깅 완료 - orderId={}", event.getOrderId());

        } catch (Exception e) {
            // 알림 실패는 로깅만 하고 예외를 전파하지 않음
            log.error("[OrderAlertEventListener] Saga 실패 알림 발송 실패 (무시됨) - orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * 보상 실패 처리 (COMPENSATION_FAILED)
     *
     * 처리 내용:
     * - 보상 트랜잭션 실패 (Critical)
     * - 수동 개입이 필요한 심각한 상황
     * - 긴급 알림 발송 (운영팀, 개발팀)
     *
     * 중요도: 높음
     * - 데이터 불일치 가능성
     * - 즉시 수동 복구 작업 필요
     *
     * @param event OrderSagaEvent (COMPENSATION_FAILED)
     */
    private void handleCompensationFailed(OrderSagaEvent event) {
        log.error("[OrderAlertEventListener] 보상 실패 처리 (Critical) - orderId={}, userId={}, error={}",
                event.getOrderId(), event.getUserId(), event.getErrorMessage());

        try {
            alertService.notifyCompensationFailure(
                    event.getOrderId(),
                    event.getUserId(),
                    event.getErrorMessage()
            );
            log.info("[OrderAlertEventListener] 보상 실패 긴급 알림 발송 완료 - orderId={}", event.getOrderId());

        } catch (Exception e) {
            // 보상 실패 알림도 실패한 경우 (매우 심각)
            log.error("[OrderAlertEventListener] 보상 실패 알림 발송 실패 (매우 심각!) - orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }
}