package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.application.order.saga.OrderSagaOrchestrator;
import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderItem;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.order.event.OrderSagaEvent;
import com.hhplus.ecommerce.domain.order.event.SagaEventType;
import com.hhplus.ecommerce.domain.order.event.OrderCompletedEvent;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.domain.user.InsufficientBalanceException;
import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import com.hhplus.ecommerce.application.order.dto.OrderItemCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * OrderSagaService - Saga Orchestrator 기반 주문 결제 시스템 (Application 계층)
 *
 * 역할:
 * - OrderSagaOrchestrator를 통한 분산 트랜잭션 관리
 * - 주문 생성, 재고 차감, 포인트 차감, 쿠폰 사용을 독립적인 Step으로 분리
 * - 결제 실패 시 자동 보상 트랜잭션 (Compensation) 실행
 *
 * ✅ Saga Orchestrator 패턴 적용:
 * - 중앙 집중식 워크플로우 관리 (OrderSagaOrchestrator)
 * - 각 Step은 독립적인 트랜잭션으로 실행 (REQUIRES_NEW)
 * - Forward Flow: 순차 실행 (Step 1 → 2 → 3 → 4)
 * - Backward Flow: LIFO 보상 (Step 4 → 3 → 2 → 1)
 *
 * Step 구성:
 * Step 1: DeductInventoryStep - 재고 차감
 * Step 2: DeductBalanceStep - 포인트 차감
 * Step 3: UseCouponStep - 쿠폰 사용 (선택적)
 * Step 4: CreateOrderStep - 주문 생성
 *
 * 보상 순서 (LIFO):
 * Step 4: CreateOrderStep - 주문 취소
 * Step 3: UseCouponStep - 쿠폰 복구
 * Step 2: DeductBalanceStep - 포인트 환불
 * Step 1: DeductInventoryStep - 재고 복구
 *
 * ✅ 트랜잭션 외부 I/O 분리:
 * - PaymentSuccessEvent, CompensationCompletedEvent, CompensationFailedEvent 발행
 * - @Async + @TransactionalEventListener(AFTER_COMMIT)에서 비동기 알림 처리
 * - 트랜잭션 내에서는 DB 작업 + 이벤트 발행만 수행
 *
 * 동시성 제어:
 * - 각 Step별로 독립적인 동시성 제어
 * - DeductInventoryStep: ProductOption 비관적 락
 * - DeductBalanceStep: UserBalanceService의 분산락 + 비관적 락
 * - UseCouponStep: UserCoupon 비관적 락
 * - CreateOrderStep: Order 생성 (락 불필요)
 *
 * MSA 전환 대비:
 * - 각 Step을 독립 서비스로 분리 가능
 * - Orchestrator를 별도 서비스로 분리 가능
 * - Step 간 의존성 최소화
 */
@Service
public class OrderSagaService {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaService.class);

    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderCalculator orderCalculator;
    private final OutboxEventPublisher outboxEventPublisher;
    private final ObjectMapper objectMapper;

    public OrderSagaService(OrderSagaOrchestrator orderSagaOrchestrator,
                          OrderRepository orderRepository,
                          ProductRepository productRepository,
                          UserRepository userRepository,
                          ApplicationEventPublisher eventPublisher,
                          OrderCalculator orderCalculator,
                          OutboxEventPublisher outboxEventPublisher,
                          ObjectMapper objectMapper) {
        this.orderSagaOrchestrator = orderSagaOrchestrator;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.orderCalculator = orderCalculator;
        this.outboxEventPublisher = outboxEventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Saga Orchestrator 기반 주문 생성 및 자동 결제
     *
     * 플로우 (Saga Orchestrator 패턴):
     * Step 1: DeductInventoryStep - 재고 차감 (독립 트랜잭션)
     * Step 2: DeductBalanceStep - 포인트 차감 (독립 트랜잭션)
     * Step 3: UseCouponStep - 쿠폰 사용 (독립 트랜잭션, 선택적)
     * Step 4: CreateOrderStep - 주문 생성 (독립 트랜잭션)
     *
     * 실패 시 자동 보상 (LIFO):
     * - OrderSagaOrchestrator가 자동으로 역순 보상 실행
     * - Step 4 → 3 → 2 → 1 순서로 compensate() 호출
     * - 각 Step의 보상 플래그를 확인하여 실행 여부 결정
     *
     * ✅ 트랜잭션 외부 I/O 분리:
     * - PaymentSuccessEvent 발행 (성공 시 알림)
     * - CompensationFailedEvent 발행 (보상 실패 시 알림)
     * - 이벤트 리스너가 AFTER_COMMIT + @Async로 비동기 처리
     *
     * 특징:
     * - 각 Step은 독립적인 트랜잭션으로 실행 (REQUIRES_NEW)
     * - Step 간 의존성 최소화 (MSA 전환 대비)
     * - 중앙 집중식 워크플로우 관리 (Orchestrator)
     * - 자동 보상 메커니즘 (LIFO)
     *
     * @param userId 사용자 ID
     * @param orderItems 주문 항목 리스트
     * @param couponId 쿠폰 ID (nullable)
     * @param finalAmount 최종 결제액 (포인트)
     * @return 생성된 주문 (결제 완료된 상태)
     * @throws InsufficientBalanceException 포인트 부족
     * @throws RuntimeException Saga 실행 실패
     */
    public Order createOrderWithPayment(Long userId,
                                       List<OrderItemDto> orderItems,
                                       Long couponId,
                                       Long finalAmount) {
        try {
            log.info("[OrderSagaService] Saga Orchestrator 주문 생성 시작 - userId={}, finalAmount={}, couponId={}",
                    userId, finalAmount, couponId);

            // ========== Step 1: 쿠폰 할인액 계산 ==========
            Long couponDiscount = (couponId != null) ? calculateCouponDiscount(couponId) : 0L;

            // OrderItemDto를 OrderItemCommand로 변환 (OrderCalculator 호출용)
            List<OrderItemCommand> orderItemCommands = orderItems.stream()
                    .map(dto -> OrderItemCommand.builder()
                            .productId(dto.getProductId())
                            .optionId(dto.getOptionId())
                            .quantity(dto.getQuantity())
                            .build())
                    .collect(java.util.stream.Collectors.toList());

            Long subtotal = orderCalculator.calculateSubtotal(orderItemCommands);

            // ========== Step 2: OrderSagaOrchestrator 실행 ==========
            // OrderSagaOrchestrator가 각 Step을 순차 실행:
            // 1. DeductInventoryStep (재고 차감)
            // 2. DeductBalanceStep (포인트 차감)
            // 3. UseCouponStep (쿠폰 사용, 선택적)
            // 4. CreateOrderStep (주문 생성)
            //
            // 실패 시 자동 보상 (LIFO):
            // 4. CreateOrderStep → 3. UseCouponStep → 2. DeductBalanceStep → 1. DeductInventoryStep
            Order order = orderSagaOrchestrator.executeSaga(
                    userId,
                    orderItems,
                    couponId,
                    couponDiscount,
                    subtotal,
                    finalAmount
            );

            log.info("[OrderSagaService] Saga Orchestrator 주문 생성 완료 - orderId={}, 결제완료",
                    order.getOrderId());

            // ========== Step 3: Saga 성공 이벤트 발행 ==========
            // ✅ 트랜잭션 외부 I/O 분리: OrderSagaEvent(COMPLETED) 발행
            // - 이벤트는 트랜잭션 커밋 후 AFTER_COMMIT + @Async 리스너에서 처리
            // - 알림 실패가 비즈니스 트랜잭션에 영향을 주지 않음
            // ✅ Outbox 패턴 통합: 실패 시 Outbox에 저장하여 배치 재시도 보장
            try {
                OrderSagaEvent event = OrderSagaEvent.completed(
                        order.getOrderId(),
                        userId,
                        finalAmount
                );
                eventPublisher.publishEvent(event);
                log.debug("[OrderSagaService] OrderSagaEvent(COMPLETED) 발행: orderId={}", order.getOrderId());
            } catch (Exception e) {
                // 이벤트 발행 실패 시 Outbox에 저장하여 배치 재시도 보장
                log.warn("[OrderSagaService] OrderSagaEvent(COMPLETED) 발행 실패 - Outbox에 저장: orderId={}, error={}",
                        order.getOrderId(), e.getMessage());

                try {
                    // OrderSagaEvent를 JSON으로 직렬화
                    OrderSagaEvent event = OrderSagaEvent.completed(
                            order.getOrderId(),
                            userId,
                            finalAmount
                    );
                    String eventPayload = objectMapper.writeValueAsString(event);

                    // Outbox에 저장 (배치가 재시도)
                    outboxEventPublisher.publishWithOutbox(
                            "SAGA_COMPLETED",
                            order.getOrderId(),
                            userId,
                            eventPayload
                    );

                    log.info("[OrderSagaService] OrderSagaEvent(COMPLETED) Outbox 저장 완료 - orderId={}, 배치가 재시도합니다",
                            order.getOrderId());

                } catch (JsonProcessingException jsonError) {
                    // JSON 직렬화 실패 (심각한 오류)
                    log.error("[OrderSagaService] OrderSagaEvent JSON 직렬화 실패 - orderId={}, error={}",
                            order.getOrderId(), jsonError.getMessage(), jsonError);
                } catch (Exception outboxError) {
                    // Outbox 저장 실패 (심각한 오류)
                    log.error("[OrderSagaService] OrderSagaEvent Outbox 저장 실패 - orderId={}, error={}",
                            order.getOrderId(), outboxError.getMessage(), outboxError);
                }
            }

            // ========== Step 4: OrderCompletedEvent 발행 (실시간 전송) ==========
            // ✅ 트랜잭션 커밋 후 발행: @TransactionalEventListener(AFTER_COMMIT)에서 데이터 플랫폼 전송
            // - OrderTransactionService와 동일한 방식으로 OrderCompletedEvent 발행
            // - 두 서비스 모두에서 일관되게 이벤트 발행하여 데이터 플랫폼 통합
            try {
                OrderCompletedEvent completedEvent = new OrderCompletedEvent(
                        order.getOrderId(),
                        userId,
                        finalAmount
                );
                eventPublisher.publishEvent(completedEvent);
                log.info("[OrderSagaService] OrderCompletedEvent 발행 (Saga): orderId={}, userId={}, amount={}",
                        order.getOrderId(), userId, finalAmount);
            } catch (Exception e) {
                // 이벤트 발행 실패는 로깅만 하고 메인 로직에 영향 주지 않음
                log.warn("[OrderSagaService] OrderCompletedEvent 발행 실패 (무시됨): orderId={}, error={}",
                        order.getOrderId(), e.getMessage());
            }

            return order;

        } catch (InsufficientBalanceException e) {
            // 포인트 부족 예외는 그대로 재발생
            log.warn("[OrderSagaService] 포인트 부족으로 주문 생성 실패 - userId={}, 필요포인트={}, error={}",
                    userId, finalAmount, e.getMessage());
            throw e;

        } catch (RuntimeException e) {
            // Saga 실행 실패 (OrderSagaOrchestrator에서 이미 보상 완료)
            log.error("[OrderSagaService] Saga Orchestrator 주문 생성 실패 (이미 보상 완료) - userId={}, error={}",
                    userId, e.getMessage(), e);

            // ✅ Saga 실패 이벤트 발행
            // OrderSagaOrchestrator에서 보상이 완료된 후 발행
            try {
                OrderSagaEvent event = OrderSagaEvent.failed(
                        null, // orderId가 없을 수 있음
                        userId,
                        e.getMessage()
                );
                eventPublisher.publishEvent(event);
                log.debug("[OrderSagaService] OrderSagaEvent(FAILED) 발행: userId={}", userId);
            } catch (Exception eventError) {
                log.warn("[OrderSagaService] OrderSagaEvent(FAILED) 발행 실패 (무시됨): userId={}, error={}",
                        userId, eventError.getMessage());
            }

            throw new RuntimeException("Order creation failed: " + e.getMessage(), e);
        }
    }

    /**
     * 보상 트랜잭션 (Compensation Transaction - Step 3b)
     *
     * ✅ CRITICAL: 상태 검증 및 비즈니스 로직 순서 보장
     * ========================================
     * 1. findByIdForUpdate() - 비관적 락 획득 (SELECT ... FOR UPDATE)
     *    - 다른 트랜잭션의 동시 접근 차단
     * 2. Order 조회 직후 즉시 상태 검증 (PENDING 확인)
     *    - 상태가 PENDING이 아니면 조기 반환 (이미 보상 처리됨)
     * 3. 상태 검증 성공 후에야 비즈니스 로직 시작
     *    - 재고 복구
     *    - 잔액 복구
     *    - 주문 상태 변경
     * 4. 모든 작업이 원자적으로 처리됨 (@Transactional)
     *
     * ⚠️ 주의: 상태 검증 전에 재고/잔액 복구 로직 실행 금지
     *
     * ✅ 트랜잭션 외부 I/O 분리:
     * - alertService.notifyCompensationComplete() 제거 → CompensationCompletedEvent 발행
     * - alertService.notifyCompensationFailure() 제거 → CompensationFailedEvent 발행
     * - 이벤트 리스너가 AFTER_COMMIT + @Async로 비동기 알림 처리
     *
     * 복구 항목:
     * - 재고 복구: 각 주문 항목별로 ProductOption의 stock 복구
     * - 잔액 복구: User의 balance 복구
     * - 주문 상태 변경: PENDING → FAILED → CANCELLED
     *
     * ✅ IMPORTANT: 이 메서드는 Step 2 (PG API 호출) 이후에 호출됨
     * ✅ 독립적인 @Transactional 메서드로 분리
     * ✅ Step 1과 분리된 트랜잭션에서 실행
     *
     * @param orderId 보상할 주문의 ID
     * @return 보상 처리된 주문 (CANCELLED 상태) 또는 이미 보상된 주문
     * @throws CompensationException 복구 중 데이터베이스 오류 발생
     */
    @Transactional
    public Order compensateOrder(Long orderId) {
        // ==================== STEP 1: Order 조회 + 비관적 락 ====================
        // findByIdForUpdate()로 SELECT ... FOR UPDATE 적용
        // 다른 트랜잭션이 이 Order 행을 쓰기/읽기 할 수 없음 (배타적 잠금)
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found for compensation: " + orderId));

        log.debug("[OrderSagaService] Order 조회 완료 (비관적 락 획득) - orderId={}, 현재상태={}",
                  orderId, order.getOrderStatus());

        // ==================== STEP 2: 상태 검증 (조회 직후 즉시) ====================
        // ✅ CRITICAL: 상태 검증은 조회 바로 직후에 수행해야 함
        // ✅ 이전에 다른 비즈니스 로직 없음
        // ✅ PENDING이 아니면 조기 반환 (이미 보상 처리됨)
        if (!order.isPending()) {
            log.warn("[OrderSagaService] 보상 대상이 아닌 주문 (이미 처리됨) - orderId={}, 현재상태={}, " +
                     "보상을 수행하지 않습니다",
                    orderId, order.getOrderStatus());
            return order;
        }

        log.info("[OrderSagaService] Order 상태 검증 성공 - orderId={}, 상태=PENDING (보상 필요)", orderId);
        log.info("[OrderSagaService] 보상 트랜잭션 시작 - orderId={}", orderId);

        try {
            // ==================== STEP 3: 재고 복구 (상태 검증 후) ====================
            // ✅ 상태 검증 성공 후에만 재고 복구 시작
            log.info("[OrderSagaService] STEP 3-1: 재고 복구 시작 - orderId={}", orderId);
            for (OrderItem item : order.getOrderItems()) {
                ProductOption option = productRepository.findOptionById(item.getOptionId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "재고 복구 중 옵션을 찾을 수 없습니다: " + item.getOptionId()));

                log.info("[OrderSagaService] 재고 복구 중 - optionId={}, 복구 수량={}, 상품명={}",
                         item.getOptionId(), item.getQuantity(), option.getName());
                option.restoreStock(item.getQuantity());
                productRepository.saveOption(option);
            }
            log.info("[OrderSagaService] STEP 3-1 완료: 재고 복구 완료 - orderId={}", orderId);

            // ==================== STEP 4: 잔액 복구 (상태 검증 후) ====================
            // ✅ 재고 복구 성공 후 잔액 복구
            log.info("[OrderSagaService] STEP 3-2: 잔액 복구 시작 - orderId={}", orderId);
            User user = userRepository.findById(order.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "잔액 복구 중 사용자를 찾을 수 없습니다: " + order.getUserId()));

            Long recoveryAmount = order.getFinalAmount();
            Long previousBalance = user.getBalance();
            log.info("[OrderSagaService] 잔액 복구 중 - userId={}, 복구액={}, 기존잔액={}, 복구후잔액={}",
                     user.getUserId(), recoveryAmount, previousBalance, previousBalance + recoveryAmount);
            user.setBalance(user.getBalance() + recoveryAmount);
            userRepository.save(user);
            log.info("[OrderSagaService] STEP 3-2 완료: 잔액 복구 완료 - orderId={}", orderId);

            // ==================== STEP 5: 주문 상태 변경 (재고/잔액 복구 후) ====================
            // ✅ 재고/잔액 복구 성공 후 주문 상태 변경
            // PENDING → FAILED → CANCELLED
            log.info("[OrderSagaService] STEP 3-3: 주문 상태 변경 시작 - orderId={}", orderId);
            try {
                order.markAsFailed();
                log.info("[OrderSagaService] 주문 상태 변경 (PENDING→FAILED) - orderId={}", orderId);

                order.cancel(); // FAILED → CANCELLED
                log.info("[OrderSagaService] 주문 상태 변경 (FAILED→CANCELLED) - orderId={}", orderId);
            } catch (Exception statusChangeError) {
                log.error("[OrderSagaService] 주문 상태 변경 중 오류 - orderId={}, error={}",
                         orderId, statusChangeError.getMessage());
                throw statusChangeError;
            }

            // ==================== STEP 6: DB 저장 ====================
            // ✅ 모든 변경사항을 DB에 저장 (이 @Transactional 내에서 원자적으로 처리)
            Order cancelledOrder = orderRepository.save(order);
            log.info("[OrderSagaService] STEP 3-3 완료: 주문 상태 변경 완료 - orderId={}, 최종상태=CANCELLED", orderId);

            // ==================== STEP 7: 보상 완료 알림 ====================
            log.info("[OrderSagaService] 보상 트랜잭션 완료 - orderId={}, 최종상태=CANCELLED", orderId);

            // ✅ 보상 완료는 Saga 실패로 간주 (FAILED 이벤트 발행)
            // - 보상이 성공했더라도 Saga 자체는 실패한 것
            // - CompensationCompletedEvent 제거됨
            // Note: 별도 알림이 필요한 경우 리스너에서 FAILED 타입을 세분화 처리 가능

            return cancelledOrder;

        } catch (Exception e) {
            log.error("[OrderSagaService] 보상 트랜잭션 중 오류 발생! 수동 개입 필요 - orderId={}, error={}, message={}",
                    orderId, e.getClass().getSimpleName(), e.getMessage(), e);

            // ✅ 보상 실패 이벤트 발행
            // - OrderSagaEvent(COMPENSATION_FAILED) 발행
            // - 수동 개입 필요한 심각한 상황
            try {
                OrderSagaEvent event = OrderSagaEvent.compensationFailed(
                        orderId,
                        order.getUserId(),
                        e.getMessage()
                );
                eventPublisher.publishEvent(event);
                log.debug("[OrderSagaService] OrderSagaEvent(COMPENSATION_FAILED) 발행: orderId={}", orderId);
            } catch (Exception eventError) {
                log.warn("[OrderSagaService] OrderSagaEvent(COMPENSATION_FAILED) 발행 실패 (무시됨): orderId={}, error={}",
                        orderId, eventError.getMessage());
            }

            throw new CompensationException("Compensation failed for order: " + orderId, e);
        }
    }

    /**
     * 쿠폰 할인액 조회 (실제 구현 필요)
     * TODO: CouponRepository를 통해 실제 할인액 조회
     */
    private Long calculateCouponDiscount(Long couponId) {
        return 0L; // 임시
    }


    /**
     * PaymentException - 결제 실패 예외
     */
    public static class PaymentException extends RuntimeException {
        public PaymentException(String message) {
            super(message);
        }

        public PaymentException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * CompensationException - 보상 트랜잭션 실패 예외 (심각함)
     */
    public static class CompensationException extends RuntimeException {
        public CompensationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}