package com.hhplus.ecommerce.presentation.order;

import com.hhplus.ecommerce.application.order.OrderService;
import com.hhplus.ecommerce.domain.order.InvalidOrderStatusException;
import com.hhplus.ecommerce.domain.order.OrderNotFoundException;
import com.hhplus.ecommerce.domain.order.UserMismatchException;
import com.hhplus.ecommerce.presentation.common.response.ErrorResponse;
import com.hhplus.ecommerce.presentation.order.request.CreateOrderRequest;
import com.hhplus.ecommerce.presentation.order.response.CancelOrderResponse;
import com.hhplus.ecommerce.presentation.order.response.CreateOrderResponse;
import com.hhplus.ecommerce.presentation.order.response.OrderDetailResponse;
import com.hhplus.ecommerce.presentation.order.response.OrderListResponse;
import com.hhplus.ecommerce.presentation.order.mapper.OrderMapper;
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
    private final OrderMapper orderMapper;

    public OrderController(OrderService orderService, OrderMapper orderMapper) {
        this.orderService = orderService;
        this.orderMapper = orderMapper;
    }

    /**
     * 3.1 주문 생성 (POST /api/orders)
     */
    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestBody CreateOrderRequest request) {
        // Presentation Request → Application Command로 변환
        var command = orderMapper.toCreateOrderCommand(request);

        // Application Service 호출
        var appResponse = orderService.createOrder(userId, command);

        // Application Response → Presentation Response로 변환
        CreateOrderResponse response = orderMapper.toCreateOrderResponse(appResponse);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 3.2 주문 상세 조회 (GET /api/orders/{order_id})
     */
    @GetMapping("/{order_id}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(
            @RequestHeader("X-USER-ID") Long userId,
            @PathVariable("order_id") Long orderId) {
        // Application Service 호출
        var appResponse = orderService.getOrderDetail(userId, orderId);

        // Application Response → Presentation Response로 변환
        OrderDetailResponse response = orderMapper.toOrderDetailResponse(appResponse);
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
        // Application Service 호출
        var appResponse = orderService.getOrderList(userId, page, size, status);

        // Application Response → Presentation Response로 변환
        OrderListResponse response = orderMapper.toOrderListResponse(appResponse);
        return ResponseEntity.ok(response);
    }

    /**
     * 3.4 주문 취소 (재고 복구) (POST /api/orders/{order_id}/cancel)
     * API 명세: docs/api/api-specification.md 3.4 섹션
     *
     * 1단계: 검증 (읽기 전용)
     * - 주문 존재 여부 확인 (ORDER_NOT_FOUND, 404)
     * - 사용자 권한 확인 (USER_MISMATCH, 404)
     * - 주문 상태 확인 (INVALID_ORDER_STATUS, 400)
     *
     * 2단계: 원자적 거래 (프록시를 통해 호출)
     * - 재고 복구
     * - 사용자 잔액 복구
     * - 주문 상태 변경 + cancelled_at 설정
     * - 쿠폰 상태 복구 (status='ACTIVE')
     *
     * @param userId 사용자 ID (X-USER-ID 헤더)
     * @param orderId 주문 ID (경로 변수)
     * @return 200 OK: 취소 완료 응답
     * @throws OrderNotFoundException 주문을 찾을 수 없음 (404 Not Found, ORDER_NOT_FOUND)
     * @throws UserMismatchException 주문 사용자 불일치 (404 Not Found, USER_MISMATCH)
     * @throws InvalidOrderStatusException 취소 불가능한 주문 상태 (400 Bad Request, INVALID_ORDER_STATUS)
     */
    @PostMapping("/{order_id}/cancel")
    public ResponseEntity<CancelOrderResponse> cancelOrder(
            @RequestHeader("X-USER-ID") Long userId,
            @PathVariable("order_id") Long orderId) {
        // Application Service 호출
        var appResponse = orderService.cancelOrder(userId, orderId);

        // Application Response → Presentation Response로 변환
        CancelOrderResponse response = orderMapper.toCancelOrderResponse(appResponse);
        return ResponseEntity.ok(response);
    }

    /**
     * 예외 처리: 주문을 찾을 수 없음 (404 Not Found)
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFoundException(OrderNotFoundException e) {
        ErrorResponse errorResponse = ErrorResponse.of(
                "ORDER_NOT_FOUND",
                "주문을 찾을 수 없습니다"
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * 예외 처리: 주문 사용자 불일치 (404 Not Found)
     */
    @ExceptionHandler(UserMismatchException.class)
    public ResponseEntity<ErrorResponse> handleUserMismatchException(UserMismatchException e) {
        ErrorResponse errorResponse = ErrorResponse.of(
                "USER_MISMATCH",
                "주문 사용자 불일치"
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * 예외 처리: 취소 불가능한 주문 상태 (400 Bad Request)
     */
    @ExceptionHandler(InvalidOrderStatusException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrderStatusException(InvalidOrderStatusException e) {
        ErrorResponse errorResponse = ErrorResponse.of(
                "INVALID_ORDER_STATUS",
                e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
}
