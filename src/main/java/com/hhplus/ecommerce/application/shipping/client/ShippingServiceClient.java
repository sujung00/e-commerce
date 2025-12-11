package com.hhplus.ecommerce.application.shipping.client;

import com.hhplus.ecommerce.application.shipping.dto.ShipmentRequest;
import com.hhplus.ecommerce.application.shipping.dto.ShipmentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class ShippingServiceClient {

    public ShipmentResponse createShipment(ShipmentRequest request) {
        log.info("[ShippingServiceClient] 배송 생성 요청 - orderId={}, recipientName={}, address={}",
            request.getOrderId(), request.getRecipientName(), request.getShippingAddress());

        String trackingNumber = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Long shipmentId = System.currentTimeMillis();

        log.info("[ShippingServiceClient] 배송 생성 완료 - shipmentId={}, trackingNumber={}",
            shipmentId, trackingNumber);

        return ShipmentResponse.builder()
            .shipmentId(shipmentId)
            .trackingNumber(trackingNumber)
            .build();
    }
}