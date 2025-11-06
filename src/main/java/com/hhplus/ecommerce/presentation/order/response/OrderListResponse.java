package com.hhplus.ecommerce.presentation.order.response;

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
        private Long orderId;
        private String orderStatus;
        private Long subtotal;
        private Long couponDiscount;
        private Long finalAmount;
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
