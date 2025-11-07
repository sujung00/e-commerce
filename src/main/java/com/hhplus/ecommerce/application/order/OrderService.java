package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderNotFoundException;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.order.InvalidOrderStatusException;
import com.hhplus.ecommerce.domain.order.UserMismatchException;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.product.ProductNotFoundException;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.presentation.order.request.CreateOrderRequest;
import com.hhplus.ecommerce.presentation.order.request.OrderItemRequest;
import com.hhplus.ecommerce.presentation.order.response.CancelOrderResponse;
import com.hhplus.ecommerce.presentation.order.response.CreateOrderResponse;
import com.hhplus.ecommerce.presentation.order.response.OrderDetailResponse;
import com.hhplus.ecommerce.presentation.order.response.OrderListResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * OrderService - 주문 관련 비즈니스 로직 (Application 계층)
 *
 * 책임:
 * - 주문 생성 플로우 조정 (1, 3단계)
 * - 주문 조회/목록 기능
 *
 * 구조:
 * - 1단계: 검증 (이 클래스에서 처리)
 * - 2단계: 원자적 거래 (OrderTransactionService에 프록시를 통해 위임)
 * - 3단계: 후처리 (이 클래스에서 처리)
 *
 * 왜 OrderTransactionService로 분리했는가?
 * - OrderService 내에서 @Transactional 메서드를 직접 호출하면
 *   Spring AOP 프록시가 작동하지 않아 트랜잭션이 적용되지 않음 (self-invocation 문제)
 * - 별도 서비스로 분리하면 Spring이 프록시를 생성하여
 *   @Transactional이 정상 작동 (프록시 체인으로 호출됨)
 *
 * 플로우:
 * OrderController
 *     ↓ (호출)
 * OrderService.createOrder()
 *     ↓ (1단계: 검증)
 * validateOrder()
 *     ↓ (2단계: 프록시를 통해 위임)
 * OrderTransactionService.executeTransactionalOrder() [프록시 생성, @Transactional 작동]
 *     ↓ (3단계: 후처리)
 * handlePostOrderProcessing()
 *     ↓
 * 응답 반환
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderTransactionService orderTransactionService;
    private final OrderCancelTransactionService orderCancelTransactionService;

    public OrderService(OrderRepository orderRepository,
                       ProductRepository productRepository,
                       UserRepository userRepository,
                       OrderTransactionService orderTransactionService,
                       OrderCancelTransactionService orderCancelTransactionService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderTransactionService = orderTransactionService;
        this.orderCancelTransactionService = orderCancelTransactionService;
    }

    /**
     * 주문 생성 (3단계 프로세스)
     *
     * 1️⃣ 1단계: 검증 (읽기 전용, 트랜잭션 없음)
     * 2️⃣ 2단계: 원자적 거래 (@Transactional, OrderTransactionService에서 처리)
     * 3️⃣ 3단계: 후처리 (outbox, 외부 전송 등, 트랜잭션 이후)
     *
     * @param userId 사용자 ID
     * @param request 주문 요청
     * @return 주문 생성 응답
     */
    public CreateOrderResponse createOrder(Long userId, CreateOrderRequest request) {
        // 사용자 존재 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 1단계: 검증 (읽기 전용, 트랜잭션 없음)
        validateOrder(user, request);

        // 금액 계산
        long subtotal = calculateSubtotal(request.getOrderItems());
        long couponDiscount = request.getCouponId() != null ? 5000L : 0L;
        long finalAmount = subtotal - couponDiscount;

        // 2단계: 원자적 거래 (프록시를 통해 호출, @Transactional 적용됨)
        // OrderTransactionService의 executeTransactionalOrder()는
        // Spring AOP 프록시를 통해 호출되므로 @Transactional이 정상 작동합니다
        Order savedOrder = orderTransactionService.executeTransactionalOrder(
                userId,
                request.getOrderItems(),
                request.getCouponId(),
                couponDiscount,
                subtotal,
                finalAmount
        );

        // 3단계: 후처리 (외부 전송, 알림 등, 트랜잭션 이후)
        handlePostOrderProcessing(savedOrder);

        return CreateOrderResponse.fromOrder(savedOrder);
    }

    /**
     * 3단계: 후처리 (트랜잭션 이후)
     *
     * 주문 완료 후 비동기로 처리해야 할 작업들:
     * - 외부 시스템 전송 (배송 시스템)
     * - Outbox 패턴 메시지 저장
     * - 알림 발송 등
     *
     * 현재는 로그만 처리 (실제 구현 필요)
     */
    private void handlePostOrderProcessing(Order order) {
        // Outbox 패턴으로 외부 전송 메시지 저장
        // outboxRepository.save(new Outbox(order.getOrderId(), "SHIPPING_REQUEST", "PENDING"));

        // 별도 배치 프로세스가 outbox를 처리하여 외부 시스템에 전송
        // (주문 완료 흐름과 독립적으로 동작)

        System.out.println("[OrderService] 주문 처리 완료: " + order.getOrderId());
    }

    /**
     * 1단계: 주문 검증 (읽기 전용, 트랜잭션 없음)
     *
     * 검증 항목:
     * - 각 주문 항목의 상품 존재 확인
     * - 각 옵션 존재 확인
     * - 각 옵션의 재고 확인 (수량 >= 재고)
     * - 사용자 잔액 확인 (잔액 >= 최종 결제액)
     * - 쿠폰 유효성 확인 (있는 경우)
     *
     * 실패 시 빠르게 400 Bad Request 응답
     */
    private void validateOrder(User user, CreateOrderRequest request) {
        // 주문 항목 검증
        for (OrderItemRequest itemRequest : request.getOrderItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(itemRequest.getProductId()));

            // 옵션 확인
            product.getOptions().stream()
                    .filter(o -> o.getOptionId().equals(itemRequest.getOptionId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("옵션을 찾을 수 없습니다"));

            // 재고 확인
            int availableStock = product.getOptions().stream()
                    .filter(o -> o.getOptionId().equals(itemRequest.getOptionId()))
                    .mapToInt(ProductOption -> ProductOption.getStock())
                    .sum();

            if (availableStock < itemRequest.getQuantity()) {
                String optionName = product.getOptions().stream()
                        .filter(o -> o.getOptionId().equals(itemRequest.getOptionId()))
                        .map(opt -> opt.getName())
                        .findFirst()
                        .orElse("알 수 없는 옵션");
                throw new IllegalArgumentException(optionName + "의 재고가 부족합니다");
            }
        }

        // 사용자 잔액 확인
        long subtotal = calculateSubtotal(request.getOrderItems());
        long finalAmount = subtotal - (request.getCouponId() != null ? 5000L : 0L);

        if (user.getBalance() < finalAmount) {
            throw new IllegalArgumentException("잔액이 부족합니다");
        }
    }

    /**
     * 소계 계산
     */
    private long calculateSubtotal(List<OrderItemRequest> orderItems) {
        long subtotal = 0;
        for (OrderItemRequest item : orderItems) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(item.getProductId()));
            subtotal += product.getPrice() * item.getQuantity();
        }
        return subtotal;
    }

    /**
     * 주문 상세 조회
     */
    public OrderDetailResponse getOrderDetail(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 권한 확인: 자신의 주문만 조회 가능
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("자신의 주문만 조회할 수 있습니다");
        }

        return OrderDetailResponse.fromOrder(order);
    }

    /**
     * 주문 목록 조회 (사용자별, 페이지네이션)
     */
    public OrderListResponse getOrderList(Long userId, int page, int size, Optional<String> status) {
        List<Order> orders = orderRepository.findByUserId(userId, page, size);
        long totalElements = orderRepository.countByUserId(userId);

        List<OrderListResponse.OrderSummary> summaries = orders.stream()
                .map(OrderListResponse.OrderSummary::fromOrder)
                .collect(Collectors.toList());

        return OrderListResponse.builder()
                .content(summaries)
                .totalElements(totalElements)
                .totalPages((int) Math.ceil((double) totalElements / size))
                .currentPage(page)
                .size(size)
                .build();
    }

    /**
     * 주문 취소 (재고 복구)
     *
     * 1단계: 검증 (읽기 전용)
     * - 주문 존재 여부 확인
     * - 사용자 권한 확인
     * - 주문 상태 확인 (COMPLETED만 취소 가능)
     *
     * 2단계: 원자적 거래 (OrderCancelTransactionService에서 처리)
     * - 재고 복구
     * - 사용자 잔액 복구
     * - 주문 상태 변경
     * - 쿠폰 상태 복구
     *
     * @param userId 사용자 ID
     * @param orderId 주문 ID
     * @return 취소 응답
     * @throws OrderNotFoundException 주문을 찾을 수 없음 (404)
     * @throws UserMismatchException 주문 사용자 불일치 (404)
     * @throws InvalidOrderStatusException 취소 불가능한 주문 상태 (400)
     */
    public CancelOrderResponse cancelOrder(Long userId, Long orderId) {
        // 1단계: 검증 (읽기 전용)
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 권한 확인 - USER_MISMATCH 예외 발생 (404 Not Found)
        if (!order.getUserId().equals(userId)) {
            throw new UserMismatchException(orderId, userId);
        }

        // 주문 상태 확인 (COMPLETED만 취소 가능) - INVALID_ORDER_STATUS 예외 발생 (400 Bad Request)
        if (!"COMPLETED".equals(order.getOrderStatus())) {
            throw new InvalidOrderStatusException(orderId, order.getOrderStatus());
        }

        // 2단계: 원자적 거래 (프록시를 통해 호출)
        CancelOrderResponse response = orderCancelTransactionService.executeTransactionalCancel(orderId, userId, order);

        return response;
    }
}
