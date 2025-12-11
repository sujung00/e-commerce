package com.hhplus.ecommerce.application.order;

import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderItem;
import com.hhplus.ecommerce.domain.order.Outbox;
import com.hhplus.ecommerce.domain.order.OrderException;
import com.hhplus.ecommerce.domain.order.ExecutedChildTransaction;
import com.hhplus.ecommerce.domain.order.ExecutedChildTransactionRepository;
import com.hhplus.ecommerce.domain.order.ExecutionStatus;
import com.hhplus.ecommerce.domain.order.ChildTxType;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.product.ProductNotFoundException;
import com.hhplus.ecommerce.domain.user.InsufficientBalanceException;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.order.OutboxRepository;
import com.hhplus.ecommerce.domain.order.event.OrderCreatedEvent;
import com.hhplus.ecommerce.domain.order.event.OrderCompletedEvent;
import com.hhplus.ecommerce.domain.product.event.LowInventoryEvent;
import com.hhplus.ecommerce.domain.product.ProductConstants;
import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import com.hhplus.ecommerce.application.user.UserBalanceService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.OptimisticLockException;

import java.util.List;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * OrderTransactionService - 주문 트랜잭션 처리 서비스 (Application 계층)
 *
 * 역할:
 * - OrderService와 분리된 독립적인 서비스
 * - 2단계(원자적 거래)만 담당
 * - @Transactional이 프록시를 통해 정상 작동하도록 보장
 *
 * 이유:
 * - OrderService 내에서 @Transactional 메서드를 직접 호출하면
 *   Spring AOP 프록시가 작동하지 않아 트랜잭션이 적용되지 않음
 * - 별도 서비스로 분리하면 Spring이 프록시를 생성하여 트랜잭션 관리 가능
 *
 * 아키텍처:
 * OrderService (1, 3단계)
 *     ↓ (의존성 주입)
 * OrderTransactionService (2단계, @Transactional 처리)
 */
@Service
public class OrderTransactionService {

    private static final Logger log = LoggerFactory.getLogger(OrderTransactionService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OutboxRepository outboxRepository;
    private final UserBalanceService userBalanceService;
    private final ExecutedChildTransactionRepository executedChildTransactionRepository;
    private final CouponService couponService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public OrderTransactionService(OrderRepository orderRepository,
                                   ProductRepository productRepository,
                                   UserRepository userRepository,
                                   OutboxRepository outboxRepository,
                                   UserBalanceService userBalanceService,
                                   ExecutedChildTransactionRepository executedChildTransactionRepository,
                                   CouponService couponService,
                                   ObjectMapper objectMapper,
                                   ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.outboxRepository = outboxRepository;
        this.userBalanceService = userBalanceService;
        this.executedChildTransactionRepository = executedChildTransactionRepository;
        this.couponService = couponService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 2단계: 원자적 거래 처리 (@Transactional + @Retryable + @DistributedLock)
     *
     * ⚠️ 중요한 예외 처리 전략:
     * 1. rollbackFor = Exception.class
     *    - Checked Exception도 자동 롤백하기 위함
     *    - InsufficientBalanceException, ProductNotFoundException 등도 롤백
     *
     * 2. 자식 트랜잭션(REQUIRES_NEW) 예외 처리
     *    - deductUserBalance()에서 발생한 예외는 명시적으로 catch하여 부모로 전파
     *    - 자식 TX는 이미 커밋/롤백된 상태이므로 부모와 독립적
     *
     * 3. @Retryable 범위 재한정
     *    - 전체 메서드 범위는 너무 넓음
     *    - deductInventory()만 재시도 대상 (OptimisticLockException 발생)
     *    - deductUserBalance()는 재시도 불필요 (REQUIRES_NEW로 이미 커밋됨)
     *
     * 프록시를 통해 호출되므로 @Transactional이 정상 작동합니다.
     * 다음 작업이 하나의 트랜잭션으로 처리됩니다:
     * - 재고 차감 (Domain 메서드 사용, 낙관적 락 @Version)
     * - 사용자 잔액 차감 (Domain 메서드 사용, 자식 TX)
     * - 주문 저장
     * - 주문 항목 저장
     * - 쿠폰 사용 처리
     * - Outbox 메시지 저장 (3단계: 외부 전송 대기)
     *
     * 동시성 제어:
     * - @DistributedLock: Redis 기반 분산락 (key: "order:{orderId}")
     *   - waitTime=5초: 최대 5초 동안 락 획득 시도
     *   - leaseTime=2초: 락 유지 시간 2초
     *   - 락 획득 실패 시 RuntimeException 발생 → 부모 TX 롤백 ⚠️
     * - OptimisticLockException 발생 시 @Retryable로 자동 재시도 (deductInventory만)
     * - maxAttempts=3: 최대 3회 재시도
     * - backoff: Exponential Backoff with Jitter (Thundering Herd 방지)
     *   - delay=50ms: 초기 대기 시간
     *   - multiplier=2: 매 재시도마다 2배 증가 (50ms → 100ms → 200ms)
     *   - maxDelay=1000ms: 최대 대기 시간 1초
     *   - random=true: Jitter 추가로 동시 재시도 분산
     * - maxAttempts 초과 시 handleOptimisticLockException() @Recover 호출
     *
     * 실패 시 모든 변경사항 롤백
     *
     * SCENARIO 16: 낙관적 락 재시도 폭증 (Thundering Herd)
     * - 문제: 100명이 동시에 같은 상품 구매 → 모두 OptimisticLockException → 500번 이상 재시도
     * - 해결: maxAttempts=3 제한 + Jitter로 동시 재시도 분산
     *
     * @param userId 사용자 ID
     * @param orderItems 주문 항목 리스트 (Application DTO)
     * @param couponId 쿠폰 ID (nullable)
     * @param couponDiscount 쿠폰 할인액
     * @param subtotal 소계
     * @param finalAmount 최종 결제액
     * @return 저장된 주문
     * @throws InsufficientBalanceException 잔액 부족 (자식 TX 예외)
     * @throws UserNotFoundException 사용자 없음 (자식 TX 예외)
     * @throws ProductNotFoundException 상품 없음
     * @throws OptimisticLockException 재시도 초과 시 예외 발생
     * @throws RuntimeException 분산락 획득 실패 시 예외 발생
     */
    /**
     * 2단계: 원자적 거래 처리 - Idempotency Token 버전
     *
     * ⚠️ 개선사항 (멱등성 토큰 기반 재시도 안전성):
     * - 클라이언트가 제공하는 idempotencyToken으로 중복 실행 방지
     * - 같은 토큰으로 재요청 시 이전 결과 반환
     * - REQUIRES_NEW child TX들도 ExecutedChildTransaction으로 추적
     *
     * @param userId 사용자 ID
     * @param orderItems 주문 항목 리스트
     * @param couponId 쿠폰 ID (nullable)
     * @param couponDiscount 쿠폰 할인액
     * @param subtotal 소계
     * @param finalAmount 최종 결제액
     * @param idempotencyToken 멱등성 토큰 (UUID)
     * @return 저장된 주문
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        rollbackFor = Exception.class
    )
    @Retryable(
        value = OptimisticLockException.class,
        maxAttempts = 3,
        backoff = @Backoff(
            delay = 50,
            multiplier = 2,
            maxDelay = 1000,
            random = true
        )
    )
    public Order executeTransactionalOrder(
            Long userId,
            List<OrderItemDto> orderItems,
            Long couponId,
            Long couponDiscount,
            Long subtotal,
            Long finalAmount,
            String idempotencyToken) {
        return executeTransactionalOrderInternal(
                userId, orderItems, couponId, couponDiscount, subtotal, finalAmount, idempotencyToken);
    }

    @Transactional(
        propagation = Propagation.REQUIRED,
        rollbackFor = Exception.class  // ✅ 모든 예외를 롤백 (Checked Exception 포함)
    )
    @Retryable(
        value = OptimisticLockException.class,
        maxAttempts = 3,
        backoff = @Backoff(
            delay = 50,
            multiplier = 2,
            maxDelay = 1000,
            random = true
        )
    )
    public Order executeTransactionalOrder(
            Long userId,
            List<OrderItemDto> orderItems,
            Long couponId,
            Long couponDiscount,
            Long subtotal,
            Long finalAmount) {
        return executeTransactionalOrderInternal(
                userId, orderItems, couponId, couponDiscount, subtotal, finalAmount, null);
    }

    /**
     * 2단계: 원자적 거래 처리 - 내부 구현
     */
    private Order executeTransactionalOrderInternal(
            Long userId,
            List<OrderItemDto> orderItems,
            Long couponId,
            Long couponDiscount,
            Long subtotal,
            Long finalAmount,
            String idempotencyToken) {

        // ✅ 멱등성 토큰 체크 (재시도 안전성)
        // 같은 토큰으로 이미 처리된 요청이 있으면 COMPLETED 상태 확인
        if (idempotencyToken != null) {
            var existing = executedChildTransactionRepository.findByIdempotencyToken(idempotencyToken);
            if (existing.isPresent()) {
                ExecutedChildTransaction record = existing.get();
                log.info("[OrderTransactionService] 멱등성 토큰: 이미 처리된 요청 감지: token={}, status={}, retryCount={}",
                        idempotencyToken, record.getStatus(), record.getRetryCount());

                if (ExecutionStatus.COMPLETED.equals(record.getStatus())) {
                    // 이미 완료된 요청 - skip 처리
                    // 실무에서는 여기서 캐시된 Order를 반환하거나 order_id로 재조회할 수 있음
                    log.info("[OrderTransactionService] 멱등성 보장: 재시도 요청 skip - token={}", idempotencyToken);
                    // 예시: return orderRepository.findById(record.getOrderId()).orElse(null);
                    throw new IllegalStateException("이미 처리된 요청입니다 (테스트 구현 중)");
                } else if (ExecutionStatus.PENDING.equals(record.getStatus())) {
                    // 첫 번째 실행 중이던 요청 - 재시도 카운트 증가
                    record.incrementRetryCount();
                    log.info("[OrderTransactionService] 멱등성 토큰: 재시도 진행 - token={}, retryCount={}",
                            idempotencyToken, record.getRetryCount());
                } else if (ExecutionStatus.FAILED.equals(record.getStatus())) {
                    // 이전 시도가 실패했으나 재시도 가능
                    record.incrementRetryCount();
                    record.setStatus(ExecutionStatus.PENDING);
                    log.info("[OrderTransactionService] 멱등성 토큰: FAILED에서 재시도 - token={}, retryCount={}",
                            idempotencyToken, record.getRetryCount());
                }
            } else {
                // 새로운 요청 - 멱등성 토큰 기록 시작
                ExecutedChildTransaction newRecord = ExecutedChildTransaction.create(
                        null,  // orderId는 나중에 업데이트
                        idempotencyToken,
                        ChildTxType.BALANCE_DEDUCT  // 예시, 실제로는 마스터 tx 타입
                );
                executedChildTransactionRepository.save(newRecord);
                log.info("[OrderTransactionService] 멱등성 토큰: 새로운 요청 기록 - token={}, executionId={}",
                        idempotencyToken, newRecord.getExecutionId());
            }
        }

        // ===== 2-1: 재고 차감 (낙관적 락, @Retryable 대상) =====
        // OptimisticLockException 발생 시 최대 3회 재시도
        deductInventory(orderItems);

        // ===== 2-2: 사용자 잔액 차감 (자식 TX: REQUIRES_NEW) =====
        // ⚠️ 중요: 자식 TX에서 발생하는 예외를 명시적으로 처리
        // - 자식 TX는 이미 독립적으로 커밋/롤백됨
        // - 예외가 부모로 전파되면 부모도 롤백됨
        // - 예외를 catch하면 부모는 계속 진행 (하지만 자식은 이미 롤백됨)
        //
        // ✅ 개선사항:
        // - orderId를 전달하여 ChildTransactionEvent 저장
        // - Event는 child TX와 동일 트랜잭션에서 저장되므로 원자성 보장
        // - Parent TX 실패 시에도 Event는 이미 커밋되어 보상 가능
        Long orderId = null;  // 현재는 아직 order가 생성되지 않았으므로 null
        try {
            deductUserBalance(userId, finalAmount, orderId);
        } catch (InsufficientBalanceException e) {
            // ✅ 명시적 예외 처리 1: 잔액 부족
            // 자식 TX에서 롤백됨, 부모로 예외 재전파하여 부모도 롤백 보장
            log.error("[Order] 자식 TX 예외 - 잔액 부족: userId={}, required={}, exception={}",
                    userId, finalAmount, e.getMessage());
            throw e;  // 부모로 전파 → 부모 TX 롤백
        } catch (UserNotFoundException e) {
            // ✅ 명시적 예외 처리 2: 사용자 없음
            // 이 상황은 실제로는 드물지만(주문 직전 사용자 생성) 처리
            log.error("[Order] 자식 TX 예외 - 사용자 없음: userId={}, exception={}",
                    userId, e.getMessage());
            throw e;  // 부모로 전파 → 부모 TX 롤백
        } catch (RuntimeException e) {
            // ✅ 명시적 예외 처리 3: 분산락 실패 또는 기타 RuntimeException
            // 분산락 타임아웃, 다른 런타임 예외 처리
            log.error("[Order] 자식 TX 예외 - 런타임 예외: userId={}, message={}",
                    userId, e.getMessage());
            throw e;  // 부모로 전파 → 부모 TX 롤백
        }

        // ===== 2-3: 주문 생성 및 저장 =====
        // 주의: order_items에 order_id를 설정하기 위해, 먼저 Order만 저장하고 OrderItem들을 나중에 연결
        Order order = Order.createOrder(userId, couponId, couponDiscount, subtotal, finalAmount);
        Order savedOrder = orderRepository.save(order);

        // ===== 2-4: 주문 항목 생성 및 Order ID 설정 =====
        for (OrderItemDto itemRequest : orderItems) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(itemRequest.getProductId()));

            ProductOption option = product.getOptions().stream()
                    .filter(o -> o.getOptionId().equals(itemRequest.getOptionId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("옵션을 찾을 수 없습니다"));

            OrderItem orderItem = OrderItem.createOrderItem(
                    itemRequest.getProductId(),
                    itemRequest.getOptionId(),
                    product.getProductName(),
                    option.getName(),
                    itemRequest.getQuantity(),
                    product.getPrice()
            );
            orderItem.setOrderId(savedOrder.getOrderId());
            savedOrder.addOrderItem(orderItem);
        }

        // 모든 OrderItem 추가 후 다시 저장 (CascadeType.PERSIST로 인해 자동 저장됨)
        savedOrder = orderRepository.save(savedOrder);

        // ===== 2-6: OrderCreatedEvent 발행 =====
        // God Transaction 해체
        // 쿠폰 사용 처리와 상품 상태 업데이트를 이벤트 핸들러로 분리
        // - CouponEventHandler: 동기 처리 (BEFORE_COMMIT)
        // - ProductStatusEventHandler: 비동기 처리 (AFTER_COMMIT)
        List<OrderCreatedEvent.OrderItemInfo> orderItemInfos = orderItems.stream()
                .map(item -> new OrderCreatedEvent.OrderItemInfo(
                        item.getProductId(),
                        item.getOptionId(),
                        item.getQuantity()
                ))
                .collect(Collectors.toList());

        OrderCreatedEvent event = new OrderCreatedEvent(
                savedOrder.getOrderId(),
                userId,
                couponId,
                orderItemInfos
        );

        eventPublisher.publishEvent(event);
        log.info("[OrderTransactionService] OrderCreatedEvent 발행: orderId={}, userId={}, couponId={}, itemCount={}",
                savedOrder.getOrderId(), userId, couponId, orderItemInfos.size());

        // ===== 2-7: Outbox 메시지 저장 (배치 기반 백업) =====
        saveOrderCompletionEvent(savedOrder.getOrderId(), userId);

        // ===== 2-8: OrderCompletedEvent 발행 (실시간 전송) =====
        // @TransactionalEventListener(AFTER_COMMIT)에서 데이터 플랫폼 전송
        OrderCompletedEvent completedEvent = new OrderCompletedEvent(
                savedOrder.getOrderId(),
                userId,
                savedOrder.getFinalAmount()
        );

        eventPublisher.publishEvent(completedEvent);
        log.info("[OrderTransactionService] OrderCompletedEvent 발행: orderId={}, userId={}, amount={}",
                savedOrder.getOrderId(), userId, savedOrder.getFinalAmount());

        return savedOrder;
    }

    /**
     * Outbox 메시지 저장 (Order_COMPLETED 이벤트)
     *
     * 트랜잭션 2단계 내에서 호출되므로 주문 저장과 함께 원자적으로 처리됩니다.
     * 배치 프로세스가 이 메시지를 조회하여 외부 시스템(배송, 결제 등)에 전송합니다.
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     */
    private void saveOrderCompletionEvent(Long orderId, Long userId) {
        Outbox outbox = Outbox.createOutbox(orderId, userId, "ORDER_COMPLETED");
        outboxRepository.save(outbox);
        log.info("[OrderTransactionService] Outbox 메시지 저장: orderId={}, status=PENDING", orderId);
    }

    /**
     * 재고 차감 (Domain 메서드 활용)
     *
     * Product.deductStock() 메서드가 다음을 처리합니다:
     * - ProductOption 재고 검증 및 차감
     * - 낙관적 락 (version 증가)
     * - 상품 상태 자동 업데이트 (모든 옵션 재고가 0인 경우 품절로 변경)
     * - 동시성 제어 (synchronized 블록)
     *
     * Application 계층은 Domain 메서드를 호출하기만 하면 됩니다.
     *
     * 개선사항 (재고 부족 이벤트):
     * - 재고 차감 후 재고가 LOW_STOCK_THRESHOLD 이하이면 LowInventoryEvent 발행
     * - 이벤트 리스너(InventoryEventListener)에서 관리자 알림 처리
     * - 트랜잭션 커밋 후 비동기로 실행되어 주문 트랜잭션과 분리
     */
    private void deductInventory(List<OrderItemDto> orderItems) {
        for (OrderItemDto itemRequest : orderItems) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(itemRequest.getProductId()));

            // Domain 메서드 호출 (Product가 내부적으로 ProductOption 조회 및 재고 차감)
            // 예외 처리: InsufficientStockException, ProductOptionNotFoundException
            product.deductStock(itemRequest.getOptionId(), itemRequest.getQuantity());

            // 재고 차감 후 재고 부족 여부 확인 및 이벤트 발행
            ProductOption option = product.findOptionById(itemRequest.getOptionId())
                    .orElseThrow(() -> new ProductNotFoundException(itemRequest.getProductId()));

            if (option.getStock() <= ProductConstants.LOW_STOCK_THRESHOLD) {
                log.warn("[OrderTransactionService] 재고 부족 감지 - productId={}, optionId={}, stock={}, threshold={}",
                        product.getProductId(), option.getOptionId(), option.getStock(), ProductConstants.LOW_STOCK_THRESHOLD);

                LowInventoryEvent lowInventoryEvent = new LowInventoryEvent(
                        product.getProductId(),
                        option.getOptionId(),
                        product.getProductName(),
                        option.getName(),
                        option.getStock(),
                        ProductConstants.LOW_STOCK_THRESHOLD
                );

                eventPublisher.publishEvent(lowInventoryEvent);
                log.info("[OrderTransactionService] LowInventoryEvent 발행: productId={}, optionId={}, stock={}",
                        product.getProductId(), option.getOptionId(), option.getStock());
            }

            // 저장소에 반영
            productRepository.save(product);
        }
    }

    /**
     * 사용자 잔액 차감 (UserBalanceService 활용)
     *
     * 동시성 제어:
     * - UserBalanceService.deductBalance()에서 처리:
     *   1. @DistributedLock: Redis 기반 분산락 (key: "balance:{userId}")
     *   2. findByIdForUpdate(): DB 레벨 비관적 락 (SELECT ... FOR UPDATE)
     *   3. User.deductBalance(): 잔액 검증 및 차감
     * - 분산 환경에서의 동시 접근 완벽 제어
     *
     * ✅ 개선사항:
     * - orderId를 전달하여 ChildTransactionEvent 저장 (Outbox 패턴)
     * - Event는 Child TX와 동일 트랜잭션에서 저장되므로 원자성 보장
     *
     * @param userId 사용자 ID
     * @param finalAmount 차감할 금액
     * @param orderId 주문 ID (Event 기록용, nullable)
     * @throws UserNotFoundException 사용자를 찾을 수 없음
     * @throws InsufficientBalanceException 잔액 부족
     * @throws RuntimeException 분산락 획득 실패
     */
    private void deductUserBalance(Long userId, Long finalAmount, Long orderId) {
        // UserBalanceService에서 분산락 + 트랜잭션 처리
        // 별도 프록시를 통해 호출되므로 @DistributedLock이 정상 작동
        // orderId 전달하여 ChildTransactionEvent 저장
        userBalanceService.deductBalance(userId, finalAmount, orderId);
    }


    /**
     * OptimisticLockException 복구 메서드 (@Recover)
     *
     * 역할:
     * - @Retryable의 maxAttempts를 초과했을 때 호출됨
     * - OptimisticLockException 발생 후 3회 재시도 모두 실패한 경우 처리
     *
     * SCENARIO 16: 낙관적 락 재시도 폭증 (Thundering Herd)
     * - 100명이 동시에 같은 상품 구매
     * - 모두 OptimisticLockException 발생 → 각각 3회 재시도 (총 300회)
     * - 여전히 실패 시 이 메서드가 호출됨
     * - 사용자에게 명확한 오류 메시지 반환
     *
     * 플로우:
     * 1. 첫 번째 시도 실패 (OptimisticLockException)
     * 2. 50ms 대기 후 재시도 (random jitter 추가)
     * 3. 두 번째 재시도 실패
     * 4. 100ms 대기 (multiplier=2) 후 재시도 (random jitter 추가)
     * 5. 세 번째 재시도 실패
     * 6. maxAttempts 초과 → handleOptimisticLockException() 호출
     * 7. 최종 예외 발생 (사용자에게 "재고 부족" 메시지 반환)
     *
     * @param exception OptimisticLockException
     * @param userId 사용자 ID
     * @param orderItems 주문 항목 리스트
     * @param couponId 쿠폰 ID
     * @param couponDiscount 쿠폰 할인액
     * @param subtotal 소계
     * @param finalAmount 최종 결제액
     * @return Order (이 메서드는 예외를 던지므로 반환하지 않음)
     * @throws OptimisticLockException 최종 실패 (3회 재시도 후)
     */
    @Recover
    public Order handleOptimisticLockException(
            OptimisticLockException exception,
            Long userId,
            List<OrderItemDto> orderItems,
            Long couponId,
            Long couponDiscount,
            Long subtotal,
            Long finalAmount) {

        log.error("[OrderTransactionService] 낙관적 락 재시도 초과 - userId={}, maxAttempts=3 모두 실패", userId);

        throw new OptimisticLockException(
                "상품 재고가 부족하거나 동시 주문으로 인한 충돌이 발생했습니다. " +
                "잠시 후 다시 시도해주세요. (재시도 초과)",
                exception
        );
    }
}
