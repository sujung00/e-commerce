package com.hhplus.ecommerce.presentation.order;

import com.hhplus.ecommerce.application.order.OrderService;
import com.hhplus.ecommerce.presentation.order.request.CreateOrderRequest;
import com.hhplus.ecommerce.presentation.order.response.CreateOrderResponse;
import com.hhplus.ecommerce.presentation.order.response.OrderDetailResponse;
import com.hhplus.ecommerce.presentation.order.response.OrderListResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * OrderController - 주문 API 엔드포인트
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 3.1 주문 생성 (POST /api/orders)
     */
    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestBody CreateOrderRequest request) {
        CreateOrderResponse response = orderService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 3.2 주문 상세 조회 (GET /api/orders/{order_id})
     */
    @GetMapping("/{order_id}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(
            @RequestHeader("X-USER-ID") Long userId,
            @PathVariable("order_id") Long orderId) {
        OrderDetailResponse response = orderService.getOrderDetail(userId, orderId);
        return ResponseEntity.ok(response);
    }

    /**
     * 3.3 주문 목록 조회 (GET /api/orders)
     */
    @GetMapping
    public ResponseEntity<OrderListResponse> getOrderList(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "status", required = false) Optional<String> status) {
        OrderListResponse response = orderService.getOrderList(userId, page, size, status);
        return ResponseEntity.ok(response);
    }
}
