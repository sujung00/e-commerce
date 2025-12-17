package com.hhplus.ecommerce.application.shipping.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
public class ShipmentCreationFailedEvent {
    private final Long orderId;
    private final String reason;
}