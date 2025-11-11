package com.hhplus.ecommerce.presentation.order.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hhplus.ecommerce.domain.order.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 주문 취소 응답 DTO
 * 3.4 주문 취소 (재고 복구) API 응답
 * API 명세: docs/api/api-specification.md 3.4 섹션 참고
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

    @JsonProperty("subtotal")
    private Long subtotal;

    @JsonProperty("coupon_discount")
    private Long couponDiscount;

    @JsonProperty("final_amount")
    private Long finalAmount;

    @JsonProperty("restored_amount")
    private Long restoredAmount;

    @JsonProperty("cancelled_at")
    private Instant cancelledAt;

    @JsonProperty("restored_items")
    private List<RestoredItem> restoredItems;

    /**
     * 복구된 주문 항목 정보
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
     * Order 엔티티에서 CancelOrderResponse로 변환
     */
    public static CancelOrderResponse fromOrder(Order order, List<RestoredItem> restoredItems) {
        return CancelOrderResponse.builder()
                .orderId(order.getOrderId())
                .orderStatus(order.getOrderStatus())
                .subtotal(order.getSubtotal())
                .couponDiscount(order.getCouponDiscount())
                .finalAmount(order.getFinalAmount())
                .restoredAmount(order.getFinalAmount())
                .cancelledAt(Instant.now())
                .restoredItems(restoredItems)
                .build();
    }
}
