package com.hhplus.ecommerce.application.order.saga.orchestration;

import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import com.hhplus.ecommerce.application.order.saga.compensation.CompensationFailureContext;
import com.hhplus.ecommerce.application.order.saga.compensation.SagaCompensationHandler;
import com.hhplus.ecommerce.application.order.saga.context.SagaContext;
import com.hhplus.ecommerce.application.order.saga.context.SagaExecutionSnapshot;
import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OrderSagaOrchestrator - Saga 오케스트레이터 패턴의 중앙 제어기
 *
 * 역할:
 * - Saga 워크플로우의 전체 실행 흐름 제어
 * - Step들을 순서대로 실행 (Forward Flow)
 * - 실패 시 보상 트랜잭션 자동 실행 (Backward Flow, LIFO)
 * - SagaContext를 통해 Step 간 데이터 공유 관리
 *
 * Saga Orchestrator 패턴:
 * - 중앙 집중식 워크플로우 관리 (Orchestration)
 * - 각 Step은 독립적으로 실행 가능한 트랜잭션 단위
 * - 실패 시 자동 보상 (LIFO: Last-In-First-Out)
 * - 명시적 순서 제어 (getOrder() 메서드)
 *
 * 실행 플로우:
 * 1. Forward Flow:
 *    - Step들을 getOrder() 순서대로 정렬
 *    - 순차적으로 execute() 호출
 *    - 각 Step 실행 후 executedSteps에 추가
 *    - 모든 Step 성공 시 Order 반환
 *
 * 2. Backward Flow (보상):
 *    - 예외 발생 시 자동 보상 시작
 *    - executedSteps를 역순(LIFO)으로 순회
 *    - 각 Step의 compensate() 호출
 *    - 보상 실패 시 로깅만 하고 계속 진행 (Best Effort)
 *
 * 의존성 주입:
 * - List<SagaStep>: Spring이 모든 SagaStep 구현체를 자동 주입
 * - 런타임에 getOrder()로 정렬하여 실행
 *
 * 트랜잭션 전략:
 * - Orchestrator 자체는 @Transactional 없음 (각 Step이 독립 트랜잭션)
 * - 각 Step은 필요 시 @Transactional(propagation = REQUIRES_NEW) 사용
 * - Step 간 트랜잭션 경계 명확히 분리
 */
@Component
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    /**
     * 모든 SagaStep 구현체 (Spring이 자동 주입)
     * - DeductInventoryStep (order=1)
     * - DeductBalanceStep (order=2)
     * - UseCouponStep (order=3)
     * - CreateOrderStep (order=4)
     */
    private final List<SagaStep> steps;

    /**
     * Step 이름 → Step 객체 매핑 (보상 시 사용)
     * - 보상 시 step 이름으로 step 객체를 빠르게 찾기 위한 Map
     */
    private final Map<String, SagaStep> stepMap;

    /**
     * 보상 실패 처리 Handler
     * - 보상 실패 시 처리 전략을 Handler에 위임
     * - Critical 여부 판단, 알림, DLQ 발행 담당
     */
    private final SagaCompensationHandler compensationHandler;

    /**
     * Order 조회용 Repository
     * - Saga 실행 완료 후 Order 조회에 사용
     */
    private final OrderRepository orderRepository;

    /**
     * 생성자 주입
     *
     * @param steps 모든 SagaStep 구현체 (Spring이 자동 주입)
     * @param compensationHandler 보상 실패 처리 Handler
     * @param orderRepository Order 조회용 Repository
     */
    public OrderSagaOrchestrator(List<SagaStep> steps,
                                SagaCompensationHandler compensationHandler,
                                OrderRepository orderRepository) {
        // getOrder() 순서대로 정렬
        this.steps = steps.stream()
                .sorted(Comparator.comparingInt(SagaStep::getOrder))
                .collect(Collectors.toList());

        // Step 이름 → Step 객체 매핑 생성
        this.stepMap = new HashMap<>();
        for (SagaStep step : this.steps) {
            this.stepMap.put(step.getName(), step);
        }

        this.compensationHandler = compensationHandler;
        this.orderRepository = orderRepository;

        log.info("[OrderSagaOrchestrator] Saga Steps 초기화 완료 (총 {}개)", steps.size());
        this.steps.forEach(step ->
                log.info("[OrderSagaOrchestrator]   - {} (order={})", step.getName(), step.getOrder())
        );
    }

    /**
     * Saga 워크플로우 실행 (Main Entry Point)
     *
     * Forward Flow:
     * 1. SagaContext 생성 (입력 데이터로 초기화)
     * 2. Step들을 순서대로 실행
     * 3. 각 Step 실행 후 executedSteps에 추가
     * 4. 모든 Step 성공 시 생성된 Order 반환
     *
     * Backward Flow (예외 발생 시):
     * 1. compensate() 메서드 호출
     * 2. executedSteps를 역순(LIFO)으로 보상
     * 3. 보상 완료 후 예외 재발생
     *
     * @param userId 사용자 ID
     * @param orderItems 주문 항목 리스트
     * @param couponId 쿠폰 ID (nullable)
     * @param couponDiscount 쿠폰 할인액
     * @param subtotal 주문 소계
     * @param finalAmount 최종 결제 금액
     * @return 생성된 주문 (결제 완료된 상태)
     * @throws RuntimeException Saga 실행 실패 시
     */
    public Order executeSaga(Long userId,
                            List<OrderItemDto> orderItems,
                            Long couponId,
                            Long couponDiscount,
                            Long subtotal,
                            Long finalAmount) {
        // ========== Step 1: SagaContext 생성 ==========
        SagaContext context = new SagaContext(
                userId,
                orderItems,
                couponId,
                couponDiscount,
                subtotal,
                finalAmount
        );

        log.info("[OrderSagaOrchestrator] Saga 실행 시작 - {}", context);

        try {
            // ========== Step 2: Forward Flow - 순차 실행 ==========
            for (SagaStep step : steps) {
                log.info("[OrderSagaOrchestrator] Step 실행 시작: {} (order={})",
                        step.getName(), step.getOrder());

                // Step 실행
                step.execute(context);

                // 실행 이력 추가 (LIFO 보상용) - Step 이름만 저장
                context.addExecutedStepName(step.getName());

                log.info("[OrderSagaOrchestrator] Step 실행 완료: {} (order={})",
                        step.getName(), step.getOrder());
            }

            // ========== Step 3: 성공 - Order 조회 후 반환 ==========
            Long orderId = context.getOrderId();
            if (orderId == null) {
                throw new IllegalStateException("Saga 실행 완료했지만 orderId가 null입니다 (CreateOrderStep 실행 안됨?)");
            }

            // DB에서 Order 조회 (CreateOrderStep에서 orderId만 저장했으므로)
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Saga 실행 완료했지만 Order를 찾을 수 없습니다: orderId=" + orderId));

            log.info("[OrderSagaOrchestrator] Saga 실행 성공 - orderId={}, 실행된 Step={}개",
                    order.getOrderId(), context.getExecutedStepCount());

            return order;

        } catch (Exception e) {
            // ========== Step 4: 실패 - Backward Flow 보상 ==========
            log.error("[OrderSagaOrchestrator] Saga 실행 실패 - 보상 트랜잭션 시작: {}",
                    e.getMessage(), e);

            // 보상 실행 (LIFO)
            compensate(context);

            // 예외 재발생
            throw new RuntimeException("Saga 실행 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 보상 트랜잭션 실행 (Backward Flow, LIFO) - 리팩토링 버전
     *
     * 역할:
     * - 실행된 Step들을 역순으로 보상
     * - 각 Step의 compensate() 메서드 호출
     * - 보상 실패 시 SagaCompensationHandler에 위임
     *
     * LIFO (Last-In-First-Out) 전략:
     * - 가장 마지막에 실행된 Step부터 보상
     * - 실행 순서: Step1 → Step2 → Step3 → Step4
     * - 보상 순서: Step4 → Step3 → Step2 → Step1
     *
     * 리팩토링된 보상 전략:
     * - Orchestrator는 "언제 보상할지"만 결정 (워크플로우 제어)
     * - Handler는 "보상 실패를 어떻게 처리할지" 담당 (실패 처리 전략)
     * - CompensationFailureContext DTO로 보상 실패 정보 전달
     *
     * 처리 플로우:
     * 1. Step 보상 실행
     * 2. 성공: 다음 Step 진행
     * 3. 실패: CompensationFailureContext 생성하여 Handler에 위임
     *    - Handler가 Critical 여부 판단
     *    - Handler가 알림, DLQ 발행 등 처리
     *
     * @param context Saga 실행 컨텍스트 (보상에 필요한 메타데이터 포함)
     */
    private void compensate(SagaContext context) {
        log.warn("[OrderSagaOrchestrator] 보상 트랜잭션 시작 (LIFO) - 실행된 Step={}개",
                context.getExecutedStepCount());

        // ========== Step 1: executedStepNames를 역순으로 가져오기 (LIFO) ==========
        List<String> executedStepNames = context.getExecutedStepNamesCopy();
        Collections.reverse(executedStepNames);

        log.info("[OrderSagaOrchestrator] 보상 순서: {}",
                String.join(" → ", executedStepNames));

        // ========== Step 2: orderId와 userId 추출 ==========
        Long orderId = context.getOrderId();
        Long userId = context.getUserId();

        // ========== Step 3: 각 Step 보상 실행 (Step 이름으로 Step 조회) ==========
        for (String stepName : executedStepNames) {
            // stepMap에서 Step 객체 조회
            SagaStep step = stepMap.get(stepName);
            if (step == null) {
                log.error("[OrderSagaOrchestrator] ⚠️ Step을 찾을 수 없습니다: stepName={}", stepName);
                continue;
            }

            try {
                log.info("[OrderSagaOrchestrator] 보상 실행 시작: {}", stepName);

                // 보상 실행
                step.compensate(context);

                log.info("[OrderSagaOrchestrator] 보상 실행 완료: {}", stepName);

            } catch (Exception compensationError) {
                // ========== 보상 실패 처리: Handler에 위임 ==========
                log.error("[OrderSagaOrchestrator] 보상 실패 - Handler에 위임: Step={}, error={}",
                        stepName, compensationError.getMessage());

                // CompensationFailureContext 생성
                CompensationFailureContext failureContext = CompensationFailureContext.from(
                        orderId,
                        userId,
                        step,
                        compensationError,
                        context
                );

                // Handler에 위임 (Critical 여부 판단 및 처리)
                compensationHandler.handleFailure(failureContext);
            }
        }

        // ========== Step 4: 보상 완료 로깅 ==========
        log.info("[OrderSagaOrchestrator] ✅ 보상 트랜잭션 완료 - 총 {}개 Step 보상 처리",
                executedStepNames.size());
    }

    /**
     * 등록된 Step 목록 반환 (디버깅용)
     *
     * @return Step 목록
     */
    public List<SagaStep> getSteps() {
        return steps;
    }

    /**
     * 등록된 Step 개수 반환 (디버깅용)
     *
     * @return Step 개수
     */
    public int getStepCount() {
        return steps.size();
    }
}