package com.hhplus.ecommerce.application.order.listener;

import com.hhplus.ecommerce.application.alert.AlertService;
import com.hhplus.ecommerce.domain.order.event.CompensationCompletedEvent;
import com.hhplus.ecommerce.domain.order.event.CompensationFailedEvent;
import com.hhplus.ecommerce.domain.order.event.PaymentSuccessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 알림 이벤트 리스너
 *
 * 역할: 주문 관련 이벤트 발생 시 알림을 트랜잭션 외부에서 비동기 처리
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
     * 결제 성공 이벤트 리스너
     *
     * 트랜잭션 커밋 후 비동기로 결제 성공 알림 발송
     *
     * 실패 처리:
     * - 알림 실패는 로깅만 하고 예외를 전파하지 않음
     * - 비즈니스 트랜잭션은 이미 성공했으므로 알림은 선택적 기능
     *
     * @param event 결제 성공 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        log.info("[OrderAlertEventListener] 결제 성공 이벤트 수신 - orderId={}, userId={}, amount={}",
                event.getOrderId(), event.getUserId(), event.getFinalAmount());

        try {
            alertService.notifyPaymentSuccess(event.getOrderId(), event.getUserId(), event.getFinalAmount());
            log.info("[OrderAlertEventListener] 결제 성공 알림 발송 완료 - orderId={}", event.getOrderId());

        } catch (Exception e) {
            // 알림 실패는 로깅만 하고 예외를 전파하지 않음
            // 비즈니스 트랜잭션은 이미 성공했으므로 알림은 선택적 기능
            log.error("[OrderAlertEventListener] 결제 성공 알림 발송 실패 (무시됨) - orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * 보상 완료 이벤트 리스너
     *
     * 트랜잭션 커밋 후 비동기로 보상 완료 알림 발송
     *
     * 실패 처리:
     * - 알림 실패는 로깅만 하고 예외를 전파하지 않음
     * - 보상 트랜잭션은 이미 성공했으므로 알림은 선택적 기능
     *
     * @param event 보상 완료 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCompensationCompleted(CompensationCompletedEvent event) {
        log.info("[OrderAlertEventListener] 보상 완료 이벤트 수신 - orderId={}, userId={}, amount={}",
                event.getOrderId(), event.getUserId(), event.getFinalAmount());

        try {
            alertService.notifyCompensationComplete(event.getOrderId(), event.getUserId(), event.getFinalAmount());
            log.info("[OrderAlertEventListener] 보상 완료 알림 발송 완료 - orderId={}", event.getOrderId());

        } catch (Exception e) {
            // 알림 실패는 로깅만 하고 예외를 전파하지 않음
            log.error("[OrderAlertEventListener] 보상 완료 알림 발송 실패 (무시됨) - orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * 보상 실패 이벤트 리스너
     *
     * 트랜잭션 커밋 후 비동기로 보상 실패 알림 발송
     *
     * 중요도: 높음
     * - 보상 실패는 수동 개입이 필요한 심각한 상황
     * - 알림 실패 시에도 로깅하여 추적 가능하도록 함
     *
     * 실패 처리:
     * - 알림 실패는 로깅만 하고 예외를 전파하지 않음
     *
     * @param event 보상 실패 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCompensationFailed(CompensationFailedEvent event) {
        log.error("[OrderAlertEventListener] 보상 실패 이벤트 수신 - orderId={}, userId={}, error={}",
                event.getOrderId(), event.getUserId(), event.getErrorMessage());

        try {
            alertService.notifyCompensationFailure(event.getOrderId(), event.getUserId(), event.getErrorMessage());
            log.info("[OrderAlertEventListener] 보상 실패 알림 발송 완료 - orderId={}", event.getOrderId());

        } catch (Exception e) {
            // 알림 실패는 로깅만 하고 예외를 전파하지 않음
            log.error("[OrderAlertEventListener] 보상 실패 알림 발송 실패 (무시됨) - orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }
}