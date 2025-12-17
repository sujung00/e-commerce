package com.hhplus.ecommerce.application.order.saga.steps;

import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import com.hhplus.ecommerce.application.order.saga.SagaContext;
import com.hhplus.ecommerce.application.order.saga.SagaStep;
import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderItem;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * DeductInventoryStep - 재고 차감 Step (Saga Step 1/4)
 *
 * 역할:
 * - 주문 항목별로 상품 옵션의 재고 차감
 * - 재고 차감 정보를 SagaContext에 기록 (보상용)
 * - 재고 부족 시 예외 발생 → Saga 보상 플로우 시작
 *
 * 실행 순서: 1번 (가장 먼저 실행)
 * - 재고 차감 실패 시 다른 Step들이 실행되지 않음
 * - 재고 부족은 주문 생성 전에 감지되어야 하므로 첫 번째로 실행
 *
 * Forward Flow (execute):
 * 1. 각 주문 항목의 ProductOption 조회
 * 2. option.decreaseStock(quantity) 호출 (재고 차감)
 * 3. productRepository.saveOption() 호출 (DB 저장)
 * 4. context.recordInventoryDeduction() 호출 (보상 메타데이터 기록)
 * 5. context.setInventoryDeducted(true) 설정 (보상 플래그)
 *
 * Backward Flow (compensate):
 * 1. context.isInventoryDeducted() 확인
 * 2. true이면 재고 복구 실행
 * 3. context.getDeductedInventory()에서 복구할 수량 조회
 * 4. option.restoreStock(quantity) 호출 (재고 복구)
 * 5. productRepository.saveOption() 호출 (DB 저장)
 *
 * 트랜잭션 전략:
 * - @Transactional(propagation = REQUIRES_NEW)
 * - 각 Step은 독립적인 트랜잭션으로 실행
 * - Step 실패 시 해당 Step만 롤백 (다른 Step에 영향 없음)
 */
@Component
public class DeductInventoryStep implements SagaStep {

    private static final Logger log = LoggerFactory.getLogger(DeductInventoryStep.class);

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public DeductInventoryStep(ProductRepository productRepository,
                               OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public String getName() {
        return "DeductInventoryStep";
    }

    @Override
    public int getOrder() {
        return 1; // 가장 먼저 실행
    }

    /**
     * 재고 차감 실행 (Forward Flow)
     *
     * 처리 로직:
     * 1. 각 주문 항목별로 ProductOption 조회
     * 2. decreaseStock() 호출 (재고 차감, 재고 부족 시 예외)
     * 3. DB 저장
     *
     * 변경 사항:
     * - SagaContext 메타데이터 기록 제거 (recordInventoryDeduction 제거)
     * - 보상 플래그 설정 제거 (setInventoryDeducted 제거)
     * - 보상 시 DB에서 Order/OrderItems를 조회하여 정보 획득
     *
     * 예외 처리:
     * - ProductOption 조회 실패: IllegalArgumentException
     * - 재고 부족: InsufficientStockException (domain에서 발생)
     * - 예외 발생 시 Orchestrator가 보상 플로우 시작
     *
     * @param context Saga 실행 컨텍스트
     * @throws Exception 재고 차감 실패 시
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(SagaContext context) throws Exception {
        // ========== 트랜잭션 정보 로깅 (독립 트랜잭션 검증) ==========
        String txName = TransactionSynchronizationManager.getCurrentTransactionName();
        boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();

        log.info("[{}] ========== 재고 차감 트랜잭션 시작 ==========", getName());
        log.info("[{}] Transaction Active: {}", getName(), txActive);
        log.info("[{}] Transaction Name: {}", getName(), txName != null ? txName : "NONE");
        log.info("[{}] 주문 항목: {}개", getName(), context.getOrderItems().size());

        if (!txActive) {
            log.error("[{}] ⚠️ 트랜잭션이 활성화되지 않음! REQUIRES_NEW 동작 실패!", getName());
            throw new IllegalStateException("트랜잭션이 활성화되지 않았습니다");
        }

        // ========== Step 1: 각 주문 항목별로 재고 차감 ==========
        for (OrderItemDto item : context.getOrderItems()) {
            // ProductOption 조회
            ProductOption option = productRepository.findOptionById(item.getOptionId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "상품 옵션을 찾을 수 없습니다: optionId=" + item.getOptionId()));

            log.info("[{}] 재고 차감 중 - optionId={}, optionName={}, 요청수량={}, 현재재고={}",
                    getName(), item.getOptionId(), option.getName(),
                    item.getQuantity(), option.getStock());

            // ========== Step 2: 재고 차감 (domain 로직) ==========
            // option.deductStock()에서 재고 부족 시 IllegalArgumentException 발생
            option.deductStock(item.getQuantity());

            // ========== Step 3: DB 저장 ==========
            productRepository.saveOption(option);

            log.info("[{}] 재고 차감 완료 - optionId={}, 차감수량={}, 남은재고={}",
                    getName(), item.getOptionId(), item.getQuantity(), option.getStock());
        }

        log.info("[{}] 재고 차감 완료 - 총 {}개 옵션 처리",
                getName(), context.getOrderItems().size());
        log.info("[{}] ========== 재고 차감 트랜잭션 종료 (커밋 예정) ==========", getName());
    }

    /**
     * 재고 복구 (Backward Flow / Compensation) - 리팩토링 버전
     *
     * 처리 로직 (Phase 2 변경):
     * 1. Step 실행 여부 확인 (context.hasExecutedStep으로 체크)
     * 2. DB에서 Order 조회 (orderId 사용)
     * 3. Order의 OrderItems에서 복구할 재고 정보 획득
     * 4. 각 OrderItem별로 재고 복구 실행
     * 5. DB 저장
     *
     * 변경 사항:
     * - context.isInventoryDeducted() 제거 → context.hasExecutedStep(getName()) 사용
     * - context.getDeductedInventory() 제거 → Order.getOrderItems()에서 정보 획득
     * - 메타데이터 의존 제거 → DB 조회 기반으로 전환
     *
     * Best Effort 보상:
     * - 보상 실패 시 예외를 발생시키지 말고 로깅만 수행
     * - Orchestrator가 다음 보상을 계속 진행할 수 있도록 함
     *
     * @param context Saga 실행 컨텍스트 (orderId 포함)
     * @throws Exception 보상 중 치명적 오류 발생 시
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensate(SagaContext context) throws Exception {
        // ========== 트랜잭션 정보 로깅 (독립 트랜잭션 검증) ==========
        String txName = TransactionSynchronizationManager.getCurrentTransactionName();
        boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();

        log.warn("[{}] ========== 재고 복구 트랜잭션 시작 (보상) ==========", getName());
        log.warn("[{}] Transaction Active: {}", getName(), txActive);
        log.warn("[{}] Transaction Name: {}", getName(), txName != null ? txName : "NONE");

        if (!txActive) {
            log.error("[{}] ⚠️ 보상 트랜잭션이 활성화되지 않음! REQUIRES_NEW 동작 실패!", getName());
            throw new IllegalStateException("보상 트랜잭션이 활성화되지 않았습니다");
        }

        // ========== Step 1: Step 실행 여부 확인 (Step 이름 기반) ==========
        if (!context.hasExecutedStep(getName())) {
            log.info("[{}] Step이 실행되지 않았으므로 보상 skip", getName());
            return;
        }

        // ========== Step 2: DB에서 Order 조회 ==========
        Long orderId = context.getOrderId();
        if (orderId == null) {
            log.warn("[{}] orderId가 null이므로 보상 skip (주문 생성 전 실패)", getName());
            return;
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "보상 중 Order를 찾을 수 없습니다: orderId=" + orderId));

        log.warn("[{}] 재고 복구 시작 - orderId={}, 복구할 항목 {}개",
                getName(), orderId, order.getOrderItems().size());

        // ========== Step 3: 각 OrderItem별로 재고 복구 ==========
        for (OrderItem orderItem : order.getOrderItems()) {
            Long optionId = orderItem.getOptionId();
            Integer quantity = orderItem.getQuantity();

            try {
                // ProductOption 조회
                ProductOption option = productRepository.findOptionById(optionId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "재고 복구 중 상품 옵션을 찾을 수 없습니다: optionId=" + optionId));

                log.info("[{}] 재고 복구 중 - optionId={}, optionName={}, 복구수량={}, 현재재고={}",
                        getName(), optionId, option.getName(), quantity, option.getStock());

                // ========== Step 4: 재고 복구 (domain 로직) ==========
                option.restoreStock(quantity);

                // ========== Step 5: DB 저장 ==========
                productRepository.saveOption(option);

                log.info("[{}] 재고 복구 완료 - optionId={}, 복구수량={}, 복구후재고={}",
                        getName(), optionId, quantity, option.getStock());

            } catch (Exception e) {
                // 개별 옵션 복구 실패는 로깅만 하고 계속 진행 (Best Effort)
                log.error("[{}] 재고 복구 실패 (무시하고 계속) - optionId={}, error={}",
                        getName(), optionId, e.getMessage(), e);
            }
        }

        log.warn("[{}] 재고 복구 완료 - 총 {}개 옵션 복구 시도",
                getName(), order.getOrderItems().size());
        log.warn("[{}] ========== 재고 복구 트랜잭션 종료 (커밋 예정) ==========", getName());
    }
}