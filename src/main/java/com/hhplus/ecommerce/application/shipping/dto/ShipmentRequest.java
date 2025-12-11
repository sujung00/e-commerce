package com.hhplus.ecommerce.application.shipping.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class ShipmentRequest {
    private final Long orderId;
    private final Long userId;
    private final String recipientName;
    private final String shippingAddress;
}