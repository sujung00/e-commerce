package com.hhplus.ecommerce.application.order.saga;

import com.hhplus.ecommerce.application.alert.AlertService;
import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import com.hhplus.ecommerce.common.exception.CompensationException;
import com.hhplus.ecommerce.common.exception.CriticalException;
import com.hhplus.ecommerce.common.exception.ErrorCode;
import com.hhplus.ecommerce.domain.order.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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
     * 관리자 알림 서비스
     * - Critical 보상 실패 시 즉시 알림 발송
     */
    private final AlertService alertService;

    /**
     * 보상 실패 DLQ (Dead Letter Queue)
     * - 보상 실패 메시지를 임시 저장하여 수동 재처리 가능
     */
    private final CompensationDLQ compensationDLQ;

    /**
     * 보상 실패 횟수 추적
     * - 각 Saga 실행마다 초기화됨 (인스턴스 변수가 아니라 로컬 변수로 관리 필요)
     * - 향후 통계 수집에 활용 가능
     */
    // Note: compensationFailureCount는 compensate() 메서드 내 로컬 변수로 관리

    /**
     * 생성자 주입
     *
     * @param steps 모든 SagaStep 구현체 (Spring이 자동 주입)
     * @param alertService 관리자 알림 서비스
     * @param compensationDLQ 보상 실패 DLQ
     */
    public OrderSagaOrchestrator(List<SagaStep> steps,
                                AlertService alertService,
                                CompensationDLQ compensationDLQ) {
        // getOrder() 순서대로 정렬
        this.steps = steps.stream()
                .sorted(Comparator.comparingInt(SagaStep::getOrder))
                .collect(Collectors.toList());

        this.alertService = alertService;
        this.compensationDLQ = compensationDLQ;

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

                // 실행 이력 추가 (LIFO 보상용)
                context.addExecutedStep(step);

                log.info("[OrderSagaOrchestrator] Step 실행 완료: {} (order={})",
                        step.getName(), step.getOrder());
            }

            // ========== Step 3: 성공 - Order 반환 ==========
            Order order = context.getOrder();
            if (order == null) {
                throw new IllegalStateException("Saga 실행 완료했지만 Order가 null입니다 (CreateOrderStep 실행 안됨?)");
            }

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
     * 보상 트랜잭션 실행 (Backward Flow, LIFO) - 개선된 버전
     *
     * 역할:
     * - 실행된 Step들을 역순으로 보상
     * - 각 Step의 compensate() 메서드 호출
     * - 보상 실패 추적 및 DLQ 발행
     * - CriticalException 발생 시 AlertService 알림 및 CompensationException 발생
     *
     * LIFO (Last-In-First-Out) 전략:
     * - 가장 마지막에 실행된 Step부터 보상
     * - 실행 순서: Step1 → Step2 → Step3 → Step4
     * - 보상 순서: Step4 → Step3 → Step2 → Step1
     *
     * 개선된 보상 전략:
     * - 보상 실패 횟수 추적 (compensationFailureCount)
     * - CriticalException: AlertService로 즉시 알림 + CompensationException 발생
     * - 일반 Exception: FailedCompensation DLQ 발행 + 계속 진행 (Best Effort)
     *
     * 처리 플로우:
     * 1. Step 보상 실행
     * 2. 성공: 다음 Step 진행
     * 3. CriticalException 발생:
     *    - 실패 횟수 증가
     *    - AlertService.notifyCriticalCompensationFailure() 호출
     *    - FailedCompensation DLQ 발행
     *    - CompensationException throw (상위로 전파)
     * 4. 일반 Exception 발생:
     *    - 실패 횟수 증가
     *    - FailedCompensation DLQ 발행
     *    - 계속 진행 (Best Effort)
     *
     * @param context Saga 실행 컨텍스트 (보상에 필요한 메타데이터 포함)
     * @throws CompensationException CriticalException 발생 시
     */
    private void compensate(SagaContext context) {
        log.warn("[OrderSagaOrchestrator] 보상 트랜잭션 시작 (LIFO) - 실행된 Step={}개",
                context.getExecutedStepCount());

        // ========== Step 1: executedSteps를 역순으로 가져오기 (LIFO) ==========
        List<SagaStep> executedSteps = context.getExecutedSteps();
        Collections.reverse(executedSteps);

        log.info("[OrderSagaOrchestrator] 보상 순서: {}",
                executedSteps.stream()
                        .map(SagaStep::getName)
                        .collect(Collectors.joining(" → "))
        );

        // ========== Step 2: 보상 실패 횟수 추적 변수 초기화 ==========
        int compensationFailureCount = 0;
        Long orderId = context.getOrder() != null ? context.getOrder().getOrderId() : null;
        Long userId = context.getUserId();

        // ========== Step 3: 각 Step 보상 실행 ==========
        for (SagaStep step : executedSteps) {
            try {
                log.info("[OrderSagaOrchestrator] 보상 실행 시작: {}", step.getName());

                // 보상 실행
                step.compensate(context);

                log.info("[OrderSagaOrchestrator] 보상 실행 완료: {}", step.getName());

            } catch (CriticalException criticalError) {
                // ========== Critical 보상 실패 처리 ==========
                compensationFailureCount++;

                log.error("[OrderSagaOrchestrator] 🚨 중요 보상 실패 (Critical) - Step={}, error={}, 실패 횟수={}",
                        step.getName(), criticalError.getMessage(), compensationFailureCount, criticalError);

                // 1. AlertService로 즉시 알림 발송
                alertService.notifyCriticalCompensationFailure(orderId, step.getName());

                // 2. FailedCompensation DLQ 발행
                FailedCompensation failedCompensation = FailedCompensation.from(
                        orderId,
                        userId,
                        step,
                        criticalError,
                        context
                );
                compensationDLQ.publish(failedCompensation);

                // 3. CompensationException 발생 (상위로 전파)
                log.error("[OrderSagaOrchestrator] ⚠️ Critical 보상 실패로 인해 보상 프로세스 중단 - orderId={}, stepName={}",
                        orderId, step.getName());

                throw new CompensationException(
                        ErrorCode.CRITICAL_COMPENSATION_FAILURE,
                        "Critical compensation failed",
                        step.getName(),
                        orderId,
                        criticalError
                );

            } catch (Exception compensationError) {
                // ========== 일반 보상 실패 처리 (Best Effort) ==========
                compensationFailureCount++;

                log.error("[OrderSagaOrchestrator] 보상 실패 (무시하고 계속) - Step={}, error={}, 실패 횟수={}",
                        step.getName(), compensationError.getMessage(), compensationFailureCount, compensationError);

                // FailedCompensation DLQ 발행
                FailedCompensation failedCompensation = FailedCompensation.from(
                        orderId,
                        userId,
                        step,
                        compensationError,
                        context
                );
                compensationDLQ.publish(failedCompensation);

                // Best Effort: 계속 진행
                log.warn("[OrderSagaOrchestrator] 일반 보상 실패는 무시하고 다음 Step 보상 계속 진행 - 남은 Step={}개",
                        executedSteps.indexOf(step));
            }
        }

        // ========== Step 4: 보상 완료 로깅 ==========
        if (compensationFailureCount == 0) {
            log.info("[OrderSagaOrchestrator] ✅ 보상 트랜잭션 완료 - 모든 Step 보상 성공 ({}개)",
                    executedSteps.size());
        } else {
            log.warn("[OrderSagaOrchestrator] ⚠️ 보상 트랜잭션 완료 (일부 실패) - 총 {}개 Step 중 {}개 실패, DLQ 확인 필요",
                    executedSteps.size(), compensationFailureCount);
        }
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