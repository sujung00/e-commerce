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
 * 주문 목록 조회 응답 DTO (Presentation layer)
 * 페이지네이션을 포함한 주문 목록 응답
 *
 * 책임:
 * - HTTP API 응답 직렬화 (@JsonProperty)
 * - Application layer와 독립적 (변환은 OrderMapper에서 처리)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderListResponse {
    private List<OrderSummary> content;
    @JsonProperty("total_elements")
    private long totalElements;
    @JsonProperty("total_pages")
    private int totalPages;
    @JsonProperty("current_page")
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

        @JsonProperty("user_id")
        private Long userId;

        @JsonProperty("order_status")
        private String orderStatus;

        @JsonProperty("final_amount")
        private Long finalAmount;

        @JsonProperty("created_at")
        private LocalDateTime createdAt;

        /**
         * Domain Order 엔티티에서 Presentation DTO로 변환
         *
         * @param order Domain Order 엔티티
         * @return Presentation layer OrderSummary
         */
        public static OrderSummary fromOrder(Order order) {
            return OrderSummary.builder()
                    .orderId(order.getOrderId())
                    .userId(order.getUserId())
                    .orderStatus(order.getOrderStatus().name())
                    .finalAmount(order.getFinalAmount())
                    .createdAt(order.getCreatedAt())
                    .build();
        }
    }
}
