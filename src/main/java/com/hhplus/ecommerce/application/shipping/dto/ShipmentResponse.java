package com.hhplus.ecommerce.application.shipping.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class ShipmentResponse {
    private final Long shipmentId;
    private final String trackingNumber;
}