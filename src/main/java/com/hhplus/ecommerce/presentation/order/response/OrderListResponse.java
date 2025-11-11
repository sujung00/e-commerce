package com.hhplus.ecommerce.presentation.order.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hhplus.ecommerce.domain.order.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 목록 조회 응답 DTO (페이지네이션)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderListResponse {
    private List<OrderSummary> content;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private int size;

    /**
     * 주문 요약 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderSummary {
        @JsonProperty("order_id")
        private Long orderId;

        @JsonProperty("order_status")
        private String orderStatus;

        private Long subtotal;

        @JsonProperty("coupon_discount")
        private Long couponDiscount;

        @JsonProperty("final_amount")
        private Long finalAmount;

        @JsonProperty("created_at")
        private LocalDateTime createdAt;

        public static OrderSummary fromOrder(Order order) {
            return OrderSummary.builder()
                    .orderId(order.getOrderId())
                    .orderStatus(order.getOrderStatus())
                    .subtotal(order.getSubtotal())
                    .couponDiscount(order.getCouponDiscount())
                    .finalAmount(order.getFinalAmount())
                    .createdAt(order.getCreatedAt())
                    .build();
        }
    }
}
