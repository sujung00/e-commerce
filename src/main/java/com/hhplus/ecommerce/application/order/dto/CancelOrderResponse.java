package com.hhplus.ecommerce.application.order.dto;

import com.hhplus.ecommerce.domain.order.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 취소 응답 (Application layer 내부 DTO)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelOrderResponse {
    private Long orderId;
    private String orderStatus;
    private Long refundAmount;
    private LocalDateTime cancelledAt;
    private List<RestoredItem> restoredItems;

    /**
     * RestoredItem - 복구된 주문 항목 정보 (Application layer)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RestoredItem {
        private Long orderItemId;
        private Long productId;
        private String productName;
        private Long optionId;
        private String optionName;
        private Integer quantity;
        private Integer restoredStock;
    }

    public static CancelOrderResponse fromOrder(Order order) {
        return CancelOrderResponse.builder()
                .orderId(order.getOrderId())
                .orderStatus(order.getOrderStatus().name())
                .refundAmount(order.getFinalAmount())
                .cancelledAt(order.getCancelledAt())
                .build();
    }
}