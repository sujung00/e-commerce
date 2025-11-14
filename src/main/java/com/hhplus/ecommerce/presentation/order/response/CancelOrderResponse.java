package com.hhplus.ecommerce.presentation.order.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hhplus.ecommerce.domain.order.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 주문 취소 응답 DTO (Presentation layer)
 * 3.4 주문 취소 (재고 복구) API 응답
 *
 * 책임:
 * - HTTP API 응답 직렬화 (@JsonProperty)
 * - Application layer와 독립적 (변환은 OrderMapper에서 처리)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelOrderResponse {
    @JsonProperty("order_id")
    private Long orderId;

    @JsonProperty("order_status")
    private String orderStatus;

    @JsonProperty("refund_amount")
    private Long refundAmount;

    @JsonProperty("cancelled_at")
    private Instant cancelledAt;

    @JsonProperty("restored_items")
    private List<RestoredItem> restoredItems;

    /**
     * RestoredItem - 복구된 주문 항목 정보
     *
     * 주문 취소 시 복구된 재고 정보를 포함하는 내부 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RestoredItem {
        @JsonProperty("order_item_id")
        private Long orderItemId;

        @JsonProperty("product_id")
        private Long productId;

        @JsonProperty("product_name")
        private String productName;

        @JsonProperty("option_id")
        private Long optionId;

        @JsonProperty("option_name")
        private String optionName;

        @JsonProperty("quantity")
        private Integer quantity;

        @JsonProperty("restored_stock")
        private Integer restoredStock;
    }

    /**
     * Domain Order 엔티티에서 Presentation DTO로 변환
     *
     * @param order Domain Order 엔티티
     * @param restoredItems 복구된 항목 리스트
     * @return Presentation layer CancelOrderResponse
     */
    public static CancelOrderResponse fromOrder(Order order, List<RestoredItem> restoredItems) {
        return CancelOrderResponse.builder()
                .orderId(order.getOrderId())
                .orderStatus(order.getOrderStatus().name())
                .refundAmount(order.getFinalAmount())
                .cancelledAt(Instant.now())
                .restoredItems(restoredItems)
                .build();
    }
}
