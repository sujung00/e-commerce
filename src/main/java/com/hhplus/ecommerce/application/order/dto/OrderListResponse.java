package com.hhplus.ecommerce.application.order.dto;

import com.hhplus.ecommerce.domain.order.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 목록 조회 응답 (Application layer 내부 DTO)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderListResponse {
    private List<OrderSummary> content;
    private Long totalElements;
    private Long totalPages;
    private Integer currentPage;
    private Integer size;

    /**
     * 주문 요약 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderSummary {
        private Long orderId;
        private Long userId;
        private String orderStatus;
        private Long finalAmount;
        private LocalDateTime createdAt;

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