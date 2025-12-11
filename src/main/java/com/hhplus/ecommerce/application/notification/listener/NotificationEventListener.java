package com.hhplus.ecommerce.application.notification.listener;

import com.hhplus.ecommerce.application.shipping.event.ShipmentCreatedEvent;
import com.hhplus.ecommerce.application.shipping.event.ShipmentCreationFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class NotificationEventListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleShipmentCreated(ShipmentCreatedEvent event) {
        log.info("[NotificationEventListener] 배송 시작 알림 - orderId={}, shipmentId={}, trackingNumber={}",
            event.getOrderId(), event.getShipmentId(), event.getTrackingNumber());

        log.info("[NotificationEventListener] 사용자에게 배송 시작 알림 전송 완료");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleShipmentCreationFailed(ShipmentCreationFailedEvent event) {
        log.error("[NotificationEventListener] 배송 실패 알림 - orderId={}, reason={}",
            event.getOrderId(), event.getReason());

        log.error("[NotificationEventListener] 관리자에게 배송 실패 알림 전송 완료");
    }
}