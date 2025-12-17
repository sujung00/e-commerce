package com.hhplus.ecommerce.application.shipping.listener;

import com.hhplus.ecommerce.application.shipping.client.ShippingServiceClient;
import com.hhplus.ecommerce.application.shipping.dto.ShipmentRequest;
import com.hhplus.ecommerce.application.shipping.dto.ShipmentResponse;
import com.hhplus.ecommerce.application.shipping.event.ShipmentCreatedEvent;
import com.hhplus.ecommerce.application.shipping.event.ShipmentCreationFailedEvent;
import com.hhplus.ecommerce.domain.order.event.OrderCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingEventListener {

    private final ShippingServiceClient shippingServiceClient;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        log.info("[ShippingEventListener] 배송 생성 시작 - orderId={}", event.getOrderId());

        try {
            ShipmentRequest request = ShipmentRequest.builder()
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .recipientName(event.getRecipientName())
                .shippingAddress(event.getShippingAddress())
                .build();

            ShipmentResponse response = shippingServiceClient.createShipment(request);

            log.info("[ShippingEventListener] 배송 생성 완료 - shipmentId={}, trackingNumber={}",
                response.getShipmentId(), response.getTrackingNumber());

            eventPublisher.publishEvent(new ShipmentCreatedEvent(
                response.getShipmentId(),
                event.getOrderId(),
                response.getTrackingNumber()
            ));

        } catch (Exception e) {
            log.error("[ShippingEventListener] 배송 생성 실패 - orderId={}, error={}",
                event.getOrderId(), e.getMessage(), e);

            eventPublisher.publishEvent(new ShipmentCreationFailedEvent(
                event.getOrderId(),
                e.getMessage()
            ));
        }
    }
}