package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderNotFoundException;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.order.InvalidOrderStatusException;
import com.hhplus.ecommerce.domain.order.UserMismatchException;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.application.order.dto.CreateOrderCommand;
import com.hhplus.ecommerce.application.order.dto.CreateOrderResponse;
import com.hhplus.ecommerce.application.order.dto.OrderDetailResponse;
import com.hhplus.ecommerce.application.order.dto.OrderListResponse;
import com.hhplus.ecommerce.application.order.dto.CancelOrderResponse;
import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto;
import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * OrderService - 주문 관련 비즈니스 로직 조정자 (Application 계층)
 *
 * 책임 (리팩토링 후):
 * 1. 주문 생성/취소 플로우 조정 (1, 3단계)
 * 2. 주문 조회/목록 조회 (순수 조회)
 * 3. 검증/계산/트랜잭션 로직을 각 담당 컴포넌트에 위임
 *
 * 아키텍처:
 * - OrderValidator: 모든 유효성 검증 담당
 * - OrderCalculator: 모든 비즈니스 계산 담당
 * - OrderTransactionService: 주문 생성 트랜잭션 담당
 * - OrderCancelTransactionService: 주문 취소 트랜잭션 담당
 *
 * 플로우 (주문 생성):
 * OrderController
 *     ↓ (호출)
 * OrderService.createOrder()
 *     ├─ 1단계: 검증 (OrderValidator 위임)
 *     ├─ 계산 (OrderCalculator 위임)
 *     ├─ 2단계: 트랜잭션 (OrderTransactionService 위임)
 *     └─ 3단계: 후처리 (이 클래스에서 처리)
 *
 * 플로우 (주문 취소):
 * OrderController
 *     ↓ (호출)
 * OrderService.cancelOrder()
 *     ├─ 1단계: 검증 (OrderValidator 위임)
 *     ├─ 2단계: 트랜잭션 (OrderCancelTransactionService 위임)
 *     └─ 3단계: 후처리 (이 클래스에서 처리)
 *
 * 왜 OrderTransactionService로 분리했는가?
 * - OrderService 내에서 @Transactional 메서드를 직접 호출하면
 *   Spring AOP 프록시가 작동하지 않아 트랜잭션이 적용되지 않음 (self-invocation 문제)
 * - 별도 서비스로 분리하면 Spring이 프록시를 생성하여
 *   @Transactional이 정상 작동 (프록시 체인으로 호출됨)
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderValidator orderValidator;
    private final OrderCalculator orderCalculator;
    private final OrderTransactionService orderTransactionService;
    private final OrderCancelTransactionService orderCancelTransactionService;

    public OrderService(OrderRepository orderRepository,
                       UserRepository userRepository,
                       OrderValidator orderValidator,
                       OrderCalculator orderCalculator,
                       OrderTransactionService orderTransactionService,
                       OrderCancelTransactionService orderCancelTransactionService) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.orderValidator = orderValidator;
        this.orderCalculator = orderCalculator;
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
     * @param command 주문 커맨드
     * @return 주문 생성 응답
     */
    public CreateOrderResponse createOrder(Long userId, CreateOrderCommand command) {
        // 사용자 존재 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 금액 계산 (OrderCalculator 위임)
        long[] prices = orderCalculator.calculatePrices(command.getOrderItems(), command.getCouponId());
        long subtotal = prices[0];
        long couponDiscount = prices[1];
        long finalAmount = prices[2];

        // 1단계: 검증 (읽기 전용, 트랜잭션 없음) (OrderValidator 위임)
        orderValidator.validateOrder(user, command.getOrderItems(), finalAmount);

        // 1-1단계: 쿠폰 소유 및 사용 가능 여부 검증 (새로운 검증)
        // 변경 사항 (2025-11-18):
        // - USER_COUPONS.order_id 삭제로 인한 쿠폰 검증 로직 추가
        // - 사용자가 쿠폰을 소유하고 있으며, 미사용 상태이며, 이미 다른 주문에 사용되지 않았는지 확인
        orderValidator.validateCouponOwnershipAndUsage(userId, command.getCouponId());

        // 2단계: 원자적 거래 (프록시를 통해 호출, @Transactional 적용됨)
        // OrderTransactionService의 executeTransactionalOrder()는
        // Spring AOP 프록시를 통해 호출되므로 @Transactional이 정상 작동합니다
        // CreateOrderCommand → CreateOrderRequestDto 변환 (Application 레이어 DTO)
        List<OrderItemDto> orderItemDtos = command.getOrderItems().stream()
                .map(cmd -> OrderItemDto.builder()
                        .productId(cmd.getProductId())
                        .optionId(cmd.getOptionId())
                        .quantity(cmd.getQuantity())
                        .build())
                .collect(Collectors.toList());

        Order savedOrder = orderTransactionService.executeTransactionalOrder(
                userId,
                orderItemDtos,
                command.getCouponId(),
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
     * 주문 상세 조회
     * ✅ @Transactional(readOnly=true):
     * - FetchType.LAZY로 인한 LazyInitializationException 방지
     * - orderItems을 lazy load하기 위해 트랜잭션 필요
     */
    @Transactional(readOnly = true)
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
     * ✅ @Transactional(readOnly=true):
     * - FetchType.LAZY로 인한 LazyInitializationException 방지
     * - 각 order의 관련 정보를 lazy load하기 위해 트랜잭션 필요
     */
    @Transactional(readOnly = true)
    public OrderListResponse getOrderList(Long userId, int page, int size, Optional<String> status) {
        List<Order> orders = orderRepository.findByUserId(userId, page, size);
        long totalElements = orderRepository.countByUserId(userId);

        List<OrderListResponse.OrderSummary> summaries = orders.stream()
                .map(OrderListResponse.OrderSummary::fromOrder)
                .collect(Collectors.toList());

        return OrderListResponse.builder()
                .content(summaries)
                .totalElements(totalElements)
                .totalPages((long) Math.ceil((double) totalElements / size))
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

        // 주문 상태 확인 (OrderValidator 위임)
        orderValidator.validateOrderStatus(order);

        // 2단계: 원자적 거래 (프록시를 통해 호출)
        CancelOrderResponse response = orderCancelTransactionService.executeTransactionalCancel(orderId, userId, order);

        return response;
    }
}
