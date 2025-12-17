package com.hhplus.ecommerce.application.order.saga.steps;

import com.hhplus.ecommerce.application.order.saga.context.SagaContext;
import com.hhplus.ecommerce.application.order.saga.orchestration.SagaStep;
import com.hhplus.ecommerce.application.user.UserBalanceService;
import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import com.hhplus.ecommerce.domain.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * DeductBalanceStep - 포인트 차감 Step (Saga Step 2/4)
 *
 * 역할:
 * - 사용자 잔액(포인트)에서 주문 금액 차감
 * - UserBalanceService를 통한 포인트 차감 및 분산락 처리
 * - 차감 정보를 SagaContext에 기록 (보상용)
 * - 포인트 부족 시 예외 발생 → Saga 보상 플로우 시작
 *
 * 실행 순서: 2번 (재고 차감 다음)
 * - 재고 차감 성공 후 포인트 차감
 * - 포인트 부족 시 재고만 복구하면 됨 (간단한 보상)
 *
 * Forward Flow (execute):
 * 1. UserBalanceService.deductBalance() 호출
 *    - 내부적으로 분산락 + 비관적 락 처리
 *    - 포인트 부족 시 InsufficientBalanceException 발생
 * 2. context.setDeductedAmount() 호출 (보상 메타데이터 기록)
 * 3. context.setBalanceDeducted(true) 설정 (보상 플래그)
 *
 * Backward Flow (compensate):
 * 1. context.isBalanceDeducted() 확인
 * 2. true이면 포인트 환불 실행
 * 3. UserBalanceService.refundBalance() 호출 (포인트 복구)
 *
 * 동시성 제어:
 * - UserBalanceService에서 분산락(Redis) + 비관적 락(DB) 처리
 * - 중복 차감 방지 및 동시성 안전성 보장
 *
 * 트랜잭션 전략:
 * - @Transactional(propagation = REQUIRES_NEW)
 * - 독립적인 트랜잭션으로 실행
 * - Step 실패 시 해당 Step만 롤백
 */
@Component
public class DeductBalanceStep implements SagaStep {

    private static final Logger log = LoggerFactory.getLogger(DeductBalanceStep.class);

    private final UserBalanceService userBalanceService;
    private final OrderRepository orderRepository;

    public DeductBalanceStep(UserBalanceService userBalanceService,
                            OrderRepository orderRepository) {
        this.userBalanceService = userBalanceService;
        this.orderRepository = orderRepository;
    }

    @Override
    public String getName() {
        return "DeductBalanceStep";
    }

    @Override
    public int getOrder() {
        return 2; // 재고 차감 다음
    }

    /**
     * 포인트 차감 실행 (Forward Flow)
     *
     * 처리 로직:
     * 1. UserBalanceService.deductBalance() 호출
     *    - 분산락 + 비관적 락으로 동시성 제어
     *    - 포인트 부족 시 InsufficientBalanceException 발생
     * 2. 차감 금액을 SagaContext에 기록 (보상용)
     * 3. 보상 플래그 설정
     *
     * 예외 처리:
     * - InsufficientBalanceException: 포인트 부족
     * - 예외 발생 시 Orchestrator가 보상 플로우 시작
     *   (재고만 복구하면 됨)
     *
     * @param context Saga 실행 컨텍스트
     * @throws Exception 포인트 차감 실패 시
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(SagaContext context) throws Exception {
        Long userId = context.getUserId();
        Long finalAmount = context.getFinalAmount();

        log.info("[{}] 포인트 차감 시작 - userId={}, 차감금액={}",
                getName(), userId, finalAmount);

        // ========== Step 1: 포인트 차감 (UserBalanceService) ==========
        // UserBalanceService에서 분산락 + 비관적 락 처리
        // 포인트 부족 시 InsufficientBalanceException 발생
        User user = userBalanceService.deductBalance(
                userId,
                finalAmount,
                null // transactionMetadata (nullable)
        );

        log.info("[{}] 포인트 차감 완료 - userId={}, 차감금액={}, 남은잔액={}",
                getName(), userId, finalAmount, user.getBalance());

        log.info("[{}] 포인트 차감 Step 완료 - userId={}, 차감금액={}",
                getName(), userId, finalAmount);
    }

    /**
     * 포인트 환불 (Backward Flow / Compensation) - 리팩토링 버전
     *
     * 처리 로직:
     * 1. Step 실행 여부 확인 (context.hasExecutedStep으로 체크)
     * 2. DB에서 Order 조회 (orderId 사용)
     * 3. Order.finalAmount에서 환불할 금액 획득
     * 4. UserBalanceService.refundBalance() 호출 (포인트 복구)
     *
     * 변경 사항:
     * - context.isBalanceDeducted() 제거 → context.hasExecutedStep(getName()) 사용
     * - context.getDeductedAmount() 제거 → Order.getFinalAmount()에서 정보 획득
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

        // ========== Step 3: Order에서 환불 금액 획득 ==========
        Long userId = context.getUserId();
        Long refundAmount = order.getFinalAmount();

        log.warn("[{}] 포인트 환불 시작 - userId={}, orderId={}, 환불금액={}",
                getName(), userId, orderId, refundAmount);

        try {
            // ========== Step 4: 포인트 환불 (UserBalanceService) ==========
            User user = userBalanceService.refundBalance(userId, refundAmount);

            log.warn("[{}] 포인트 환불 완료 - userId={}, 환불금액={}, 환불후잔액={}",
                    getName(), userId, refundAmount, user.getBalance());

        } catch (Exception e) {
            // 환불 실패는 로깅만 하고 예외를 전파하지 않음 (Best Effort)
            log.error("[{}] 포인트 환불 실패 (무시하고 계속) - userId={}, 환불금액={}, error={}",
                    getName(), userId, refundAmount, e.getMessage(), e);
        }
    }
}