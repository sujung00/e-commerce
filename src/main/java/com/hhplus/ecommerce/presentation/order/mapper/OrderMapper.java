package com.hhplus.ecommerce.presentation.order.mapper;

import com.hhplus.ecommerce.presentation.order.request.CreateOrderRequest;
import com.hhplus.ecommerce.presentation.order.request.OrderItemRequest;
import com.hhplus.ecommerce.application.order.dto.CreateOrderCommand;
import com.hhplus.ecommerce.application.order.dto.OrderItemCommand;
import com.hhplus.ecommerce.application.order.dto.CreateOrderResponse;
import com.hhplus.ecommerce.application.order.dto.OrderDetailResponse;
import com.hhplus.ecommerce.application.order.dto.OrderListResponse;
import com.hhplus.ecommerce.application.order.dto.CancelOrderResponse;
import com.hhplus.ecommerce.presentation.order.response.OrderItemResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * OrderMapper - Presentation layer와 Application layer 간의 DTO 변환
 *
 * 책임:
 * - Presentation Request DTO → Application Command 변환
 * - Application Response DTO → Presentation Response DTO 변환
 *
 * 아키텍처 원칙:
 * - Application layer는 Presentation layer DTO에 독립적 (자체 DTO 사용)
 * - Presentation layer의 @JsonProperty 같은 직렬화 로직은 이곳에서만 처리
 * - 각 계층이 자신의 DTO를 소유하고 관리하여 계층 간 의존성 제거
 */
@Component
public class OrderMapper {

    /**
     * Presentation CreateOrderRequest → Application CreateOrderCommand로 변환
     */
    public CreateOrderCommand toCreateOrderCommand(CreateOrderRequest request) {
        return CreateOrderCommand.builder()
                .orderItems(request.getOrderItems().stream()
                        .map(this::toOrderItemCommand)
                        .collect(Collectors.toList()))
                .couponId(request.getCouponId())
                .build();
    }

    /**
     * Presentation OrderItemRequest → Application OrderItemCommand로 변환
     */
    private OrderItemCommand toOrderItemCommand(OrderItemRequest request) {
        return OrderItemCommand.builder()
                .productId(request.getProductId())
                .optionId(request.getOptionId())
                .quantity(request.getQuantity())
                .build();
    }

    /**
     * Application CreateOrderResponse → Presentation CreateOrderResponse로 변환
     */
    public com.hhplus.ecommerce.presentation.order.response.CreateOrderResponse toCreateOrderResponse(CreateOrderResponse appResponse) {
        return com.hhplus.ecommerce.presentation.order.response.CreateOrderResponse.builder()
                .orderId(appResponse.getOrderId())
                .userId(appResponse.getUserId())
                .orderStatus(appResponse.getOrderStatus())
                .subtotal(appResponse.getSubtotal())
                .couponDiscount(appResponse.getCouponDiscount())
                .couponId(appResponse.getCouponId())
                .finalAmount(appResponse.getFinalAmount())
                .orderItems(appResponse.getOrderItems().stream()
                        .map(item -> OrderItemResponse.builder()
                                .orderItemId(item.getOrderItemId())
                                .productId(item.getProductId())
                                .productName(item.getProductName())
                                .optionId(item.getOptionId())
                                .optionName(item.getOptionName())
                                .quantity(item.getQuantity())
                                .price(item.getPrice())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(appResponse.getCreatedAt())
                .build();
    }

    /**
     * Application OrderDetailResponse → Presentation OrderDetailResponse로 변환
     */
    public com.hhplus.ecommerce.presentation.order.response.OrderDetailResponse toOrderDetailResponse(OrderDetailResponse appResponse) {
        return com.hhplus.ecommerce.presentation.order.response.OrderDetailResponse.builder()
                .orderId(appResponse.getOrderId())
                .userId(appResponse.getUserId())
                .orderStatus(appResponse.getOrderStatus())
                .subtotal(appResponse.getSubtotal())
                .couponDiscount(appResponse.getCouponDiscount())
                .couponId(appResponse.getCouponId())
                .finalAmount(appResponse.getFinalAmount())
                .orderItems(appResponse.getOrderItems().stream()
                        .map(item -> OrderItemResponse.builder()
                                .orderItemId(item.getOrderItemId())
                                .productId(item.getProductId())
                                .productName(item.getProductName())
                                .optionId(item.getOptionId())
                                .optionName(item.getOptionName())
                                .quantity(item.getQuantity())
                                .price(item.getPrice())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(appResponse.getCreatedAt())
                .build();
    }

    /**
     * Application OrderListResponse → Presentation OrderListResponse로 변환
     */
    public com.hhplus.ecommerce.presentation.order.response.OrderListResponse toOrderListResponse(OrderListResponse appResponse) {
        return com.hhplus.ecommerce.presentation.order.response.OrderListResponse.builder()
                .content(appResponse.getContent().stream()
                        .map(summary -> com.hhplus.ecommerce.presentation.order.response.OrderListResponse.OrderSummary.builder()
                                .orderId(summary.getOrderId())
                                .userId(summary.getUserId())
                                .orderStatus(summary.getOrderStatus())
                                .finalAmount(summary.getFinalAmount())
                                .createdAt(summary.getCreatedAt())
                                .build())
                        .collect(Collectors.toList()))
                .totalElements(appResponse.getTotalElements())
                .totalPages((int) (long) appResponse.getTotalPages())
                .currentPage(appResponse.getCurrentPage())
                .size(appResponse.getSize())
                .build();
    }

    /**
     * Application CancelOrderResponse → Presentation CancelOrderResponse로 변환
     */
    public com.hhplus.ecommerce.presentation.order.response.CancelOrderResponse toCancelOrderResponse(CancelOrderResponse appResponse) {
        return com.hhplus.ecommerce.presentation.order.response.CancelOrderResponse.builder()
                .orderId(appResponse.getOrderId())
                .orderStatus(appResponse.getOrderStatus())
                .refundAmount(appResponse.getRefundAmount())
                .cancelledAt(java.time.Instant.now())
                .build();
    }
}