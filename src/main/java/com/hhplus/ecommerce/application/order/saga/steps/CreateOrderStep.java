package com.hhplus.ecommerce.application.order.saga.steps;

import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import com.hhplus.ecommerce.application.order.saga.SagaContext;
import com.hhplus.ecommerce.application.order.saga.SagaStep;
import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderItem;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.order.OrderStatus;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * CreateOrderStep - 주문 생성 Step (Saga Step 4/4)
 *
 * 역할:
 * - Order 엔티티 생성 및 DB 저장
 * - OrderItem 엔티티 생성 및 Order와 연관관계 설정
 * - 생성된 Order를 SagaContext에 저장
 * - 주문 생성 실패 시 예외 발생 → Saga 보상 플로우 시작
 *
 * 실행 순서: 4번 (마지막 Step)
 * - 재고 차감, 포인트 차감, 쿠폰 사용이 모두 성공한 후 실행
 * - 주문 생성 실패 시 모든 이전 Step들을 보상해야 함
 *
 * Forward Flow (execute):
 * 1. Order 엔티티 생성 (userId, couponId, finalAmount, orderStatus)
 * 2. OrderItem 엔티티 리스트 생성
 * 3. Order와 OrderItem 연관관계 설정
 * 4. orderRepository.save() 호출 (DB 저장)
 * 5. context.setOrder() 호출 (생성된 Order 저장)
 * 6. context.setOrderCreated(true) 설정 (보상 플래그)
 *
 * Backward Flow (compensate):
 * 1. context.isOrderCreated() 확인
 * 2. true이면 주문 취소 실행
 * 3. Order 조회 (비관적 락)
 * 4. order.markAsFailed() 호출 (PENDING → FAILED)
 * 5. order.cancel() 호출 (FAILED → CANCELLED)
 * 6. DB 저장
 *
 * 트랜잭션 전략:
 * - @Transactional(propagation = REQUIRES_NEW)
 * - 각 Step은 독립적인 트랜잭션으로 실행
 * - Step 실패 시 해당 Step만 롤백 (다른 Step에 영향 없음)
 */
@Component
public class CreateOrderStep implements SagaStep {

    private static final Logger log = LoggerFactory.getLogger(CreateOrderStep.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public CreateOrderStep(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Override
    public String getName() {
        return "CreateOrderStep";
    }

    @Override
    public int getOrder() {
        return 4; // 마지막 Step
    }

    /**
     * 주문 생성 (Forward Flow)
     *
     * 처리 로직:
     * 1. Order 엔티티 생성
     * 2. OrderItem 엔티티 리스트 생성
     * 3. Order와 OrderItem 연관관계 설정
     * 4. DB 저장
     * 5. SagaContext에 생성된 Order 저장
     *
     * 예외 처리:
     * - DB 저장 실패: RuntimeException
     * - 예외 발생 시 Orchestrator가 보상 플로우 시작
     *   (재고, 포인트, 쿠폰 모두 복구)
     *
     * @param context Saga 실행 컨텍스트
     * @throws Exception 주문 생성 실패 시
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(SagaContext context) throws Exception {
        // ========== 트랜잭션 정보 로깅 (독립 트랜잭션 검증) ==========
        String txName = TransactionSynchronizationManager.getCurrentTransactionName();
        boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();

        log.info("[{}] ========== 주문 생성 트랜잭션 시작 ==========", getName());
        log.info("[{}] Transaction Active: {}", getName(), txActive);
        log.info("[{}] Transaction Name: {}", getName(), txName != null ? txName : "NONE");

        if (!txActive) {
            log.error("[{}] ⚠️ 트랜잭션이 활성화되지 않음! REQUIRES_NEW 동작 실패!", getName());
            throw new IllegalStateException("트랜잭션이 활성화되지 않았습니다");
        }

        Long userId = context.getUserId();
        Long couponId = context.getCouponId();
        Long finalAmount = context.getFinalAmount();

        log.info("[{}] 주문 생성 시작 - userId={}, couponId={}, finalAmount={}",
                getName(), userId, couponId, finalAmount);

        // ========== Step 1: OrderItem 엔티티 리스트 생성 ==========
        // Product와 ProductOption을 조회하여 필요한 정보 가져오기
        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderItemDto itemDto : context.getOrderItems()) {
            // Product 조회 (productName, price)
            Product product = productRepository.findById(itemDto.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "상품을 찾을 수 없습니다: productId=" + itemDto.getProductId()));

            // ProductOption 조회 (optionName)
            ProductOption option = productRepository.findOptionById(itemDto.getOptionId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "상품 옵션을 찾을 수 없습니다: optionId=" + itemDto.getOptionId()));

            // OrderItem 생성 (createOrderItem 팩토리 메서드 사용)
            OrderItem orderItem = OrderItem.createOrderItem(
                    product.getProductId(),
                    option.getOptionId(),
                    product.getProductName(),  // 스냅샷: 주문 시점의 상품명
                    option.getName(),          // 스냅샷: 주문 시점의 옵션명
                    itemDto.getQuantity(),
                    product.getPrice()         // 스냅샷: 주문 시점의 단가
            );
            orderItems.add(orderItem);

            log.info("[{}] OrderItem 생성 - productId={}, productName={}, optionId={}, optionName={}, quantity={}, unitPrice={}",
                    getName(), product.getProductId(), product.getProductName(),
                    option.getOptionId(), option.getName(),
                    itemDto.getQuantity(), product.getPrice());
        }

        log.info("[{}] OrderItem 리스트 생성 완료 - orderItems={}개",
                getName(), orderItems.size());

        // ========== Step 2: Order 엔티티 생성 (Builder로 orderItems 설정) ==========
        Order order = Order.builder()
                .userId(userId)
                .couponId(couponId)
                .couponDiscount(context.getCouponDiscount())
                .subtotal(context.getSubtotal())
                .finalAmount(finalAmount)
                .orderStatus(OrderStatus.PENDING) // 초기 상태: PENDING
                .orderItems(orderItems)           // Builder로 orderItems 설정
                .build();

        log.info("[{}] Order 엔티티 생성 완료 - userId={}, orderStatus={}, orderItems={}개",
                getName(), userId, order.getOrderStatus(), orderItems.size());

        // ========== Step 4: DB에 저장 ==========
        Order savedOrder = orderRepository.save(order);

        log.info("[{}] 주문 생성 완료 - orderId={}, userId={}, orderStatus={}, finalAmount={}",
                getName(), savedOrder.getOrderId(), userId,
                savedOrder.getOrderStatus(), finalAmount);

        // ========== Step 5: SagaContext에 생성된 Order 저장 ==========
        context.setOrder(savedOrder);

        // ========== Step 6: 보상 플래그 설정 ==========
        context.setOrderCreated(true);

        log.info("[{}] 주문 생성 Step 완료 - orderId={}, orderItems={}개",
                getName(), savedOrder.getOrderId(), orderItems.size());
        log.info("[{}] ========== 주문 생성 트랜잭션 종료 (커밋 예정) ==========", getName());
    }

    /**
     * 주문 취소 (Backward Flow / Compensation)
     *
     * 처리 로직:
     * 1. context.isOrderCreated() 확인
     * 2. true이면 주문 취소 실행, false이면 skip
     * 3. context.getOrder()에서 주문 조회
     * 4. order.markAsFailed() 호출 (PENDING → FAILED)
     * 5. order.cancel() 호출 (FAILED → CANCELLED)
     * 6. DB 저장
     *
     * Best Effort 보상:
     * - 보상 실패 시 예외를 발생시키지 말고 로깅만 수행
     * - Orchestrator가 다음 보상을 계속 진행할 수 있도록 함
     *
     * @param context Saga 실행 컨텍스트 (보상 메타데이터 포함)
     * @throws Exception 보상 중 치명적 오류 발생 시
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensate(SagaContext context) throws Exception {
        // ========== 트랜잭션 정보 로깅 (독립 트랜잭션 검증) ==========
        String txName = TransactionSynchronizationManager.getCurrentTransactionName();
        boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();

        log.warn("[{}] ========== 주문 취소 트랜잭션 시작 (보상) ==========", getName());
        log.warn("[{}] Transaction Active: {}", getName(), txActive);
        log.warn("[{}] Transaction Name: {}", getName(), txName != null ? txName : "NONE");

        if (!txActive) {
            log.error("[{}] ⚠️ 보상 트랜잭션이 활성화되지 않음! REQUIRES_NEW 동작 실패!", getName());
            throw new IllegalStateException("보상 트랜잭션이 활성화되지 않았습니다");
        }

        // ========== Step 1: 보상 플래그 확인 ==========
        if (!context.isOrderCreated()) {
            log.info("[{}] 주문 생성이 실행되지 않았으므로 보상 skip", getName());
            return;
        }

        Order order = context.getOrder();
        if (order == null) {
            log.warn("[{}] Order가 null이므로 보상 skip", getName());
            return;
        }

        Long orderId = order.getOrderId();

        log.warn("[{}] 주문 취소 시작 - orderId={}, 현재상태={}",
                getName(), orderId, order.getOrderStatus());

        try {
            // ========== Step 2: Order 조회 (비관적 락) ==========
            Order dbOrder = orderRepository.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "주문 취소 중 Order를 찾을 수 없습니다: orderId=" + orderId));

            log.info("[{}] Order 조회 완료 (비관적 락 획득) - orderId={}, 현재상태={}",
                    getName(), orderId, dbOrder.getOrderStatus());

            // ========== Step 3: 주문 상태 변경 (PENDING → FAILED → CANCELLED) ==========
            if (dbOrder.isPending()) {
                // PENDING → FAILED
                dbOrder.markAsFailed();
                log.info("[{}] 주문 상태 변경 (PENDING → FAILED) - orderId={}",
                        getName(), orderId);

                // FAILED → CANCELLED
                dbOrder.cancel();
                log.info("[{}] 주문 상태 변경 (FAILED → CANCELLED) - orderId={}",
                        getName(), orderId);

                // ========== Step 4: DB에 저장 ==========
                orderRepository.save(dbOrder);

                log.warn("[{}] 주문 취소 완료 - orderId={}, 최종상태={}",
                        getName(), orderId, dbOrder.getOrderStatus());
            } else {
                log.warn("[{}] 주문 상태가 PENDING이 아니므로 취소 skip - orderId={}, 현재상태={}",
                        getName(), orderId, dbOrder.getOrderStatus());
            }

        } catch (Exception e) {
            // 주문 취소 실패는 로깅만 하고 예외를 전파하지 않음 (Best Effort)
            log.error("[{}] 주문 취소 실패 (무시하고 계속) - orderId={}, error={}",
                    getName(), orderId, e.getMessage(), e);
        }

        log.warn("[{}] ========== 주문 취소 트랜잭션 종료 (커밋 예정) ==========", getName());
    }
}