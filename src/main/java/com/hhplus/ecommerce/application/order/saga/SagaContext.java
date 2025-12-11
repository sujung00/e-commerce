package com.hhplus.ecommerce.application.order.saga;

import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import com.hhplus.ecommerce.domain.order.Order;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SagaContext - Saga 오케스트레이터의 실행 컨텍스트 (State Management)
 *
 * 역할:
 * - Saga 워크플로우 전체에서 공유되는 상태 및 데이터 관리
 * - Step 간 데이터 전달 및 공유 메커니즘 제공
 * - 보상 트랜잭션을 위한 메타데이터 저장
 * - 실행된 Step 추적 (LIFO 보상을 위한)
 *
 * 주요 구성 요소:
 * 1. 입력 데이터: userId, orderItems, couponId, 금액 정보
 * 2. 실행 결과: order (생성된 주문 엔티티)
 * 3. 보상 플래그: 각 Step의 실행 여부 추적
 * 4. 메타데이터: 보상에 필요한 세부 정보
 * 5. 실행 이력: executedSteps (LIFO 보상용)
 *
 * 보상 플래그:
 * - inventoryDeducted: 재고 차감 여부
 * - balanceDeducted: 포인트 차감 여부
 * - couponUsed: 쿠폰 사용 여부
 * - orderCreated: 주문 생성 여부
 *
 * 메타데이터:
 * - deductedAmount: 차감된 포인트 금액 (환불용)
 * - usedCouponId: 사용된 쿠폰 ID (복구용)
 * - deductedInventory: 차감된 재고 정보 (복구용)
 */
@Getter
@Setter
public class SagaContext {

    // ========== 입력 데이터 (요청 파라미터) ==========
    /**
     * 주문 사용자 ID
     */
    private Long userId;

    /**
     * 주문 항목 리스트
     */
    private List<OrderItemDto> orderItems;

    /**
     * 사용할 쿠폰 ID (nullable)
     */
    private Long couponId;

    /**
     * 쿠폰 할인액
     */
    private Long couponDiscount;

    /**
     * 주문 소계 (할인 전)
     */
    private Long subtotal;

    /**
     * 최종 결제 금액 (쿠폰 할인 적용 후)
     */
    private Long finalAmount;

    // ========== 실행 결과 ==========
    /**
     * 생성된 주문 엔티티 (CreateOrderStep에서 설정)
     */
    private Order order;

    // ========== 보상 플래그 (각 Step의 실행 여부 추적) ==========
    /**
     * 재고 차감 완료 여부 (DeductInventoryStep)
     */
    private boolean inventoryDeducted = false;

    /**
     * 포인트 차감 완료 여부 (DeductBalanceStep)
     */
    private boolean balanceDeducted = false;

    /**
     * 쿠폰 사용 완료 여부 (UseCouponStep)
     */
    private boolean couponUsed = false;

    /**
     * 주문 생성 완료 여부 (CreateOrderStep)
     */
    private boolean orderCreated = false;

    // ========== 메타데이터 (보상 트랜잭션에 필요한 세부 정보) ==========
    /**
     * 차감된 포인트 금액 (환불 시 사용)
     */
    private Long deductedAmount;

    /**
     * 사용된 쿠폰 ID (복구 시 사용)
     */
    private Long usedCouponId;

    /**
     * 차감된 재고 정보 (productId → quantity)
     * Key: ProductOption ID, Value: 차감된 수량
     */
    private Map<Long, Integer> deductedInventory = new HashMap<>();

    // ========== 실행 이력 (LIFO 보상용) ==========
    /**
     * 실행된 Step 리스트 (순서대로 추가)
     * 보상 시 역순(LIFO)으로 실행
     */
    private List<SagaStep> executedSteps = new ArrayList<>();

    // ========== 생성자 ==========
    /**
     * SagaContext 생성자
     *
     * @param userId 사용자 ID
     * @param orderItems 주문 항목 리스트
     * @param couponId 쿠폰 ID (nullable)
     * @param couponDiscount 쿠폰 할인액
     * @param subtotal 주문 소계
     * @param finalAmount 최종 결제 금액
     */
    public SagaContext(Long userId,
                       List<OrderItemDto> orderItems,
                       Long couponId,
                       Long couponDiscount,
                       Long subtotal,
                       Long finalAmount) {
        this.userId = userId;
        this.orderItems = orderItems;
        this.couponId = couponId;
        this.couponDiscount = couponDiscount;
        this.subtotal = subtotal;
        this.finalAmount = finalAmount;
    }

    // ========== Helper Methods ==========

    /**
     * 실행된 Step 추가 (LIFO 보상을 위한 추적)
     *
     * @param step 실행 완료된 Step
     */
    public void addExecutedStep(SagaStep step) {
        this.executedSteps.add(step);
    }

    /**
     * 재고 차감 정보 기록
     *
     * @param optionId ProductOption ID
     * @param quantity 차감된 수량
     */
    public void recordInventoryDeduction(Long optionId, Integer quantity) {
        this.deductedInventory.put(optionId, quantity);
    }

    /**
     * 실행된 Step 개수 반환
     *
     * @return 실행된 Step 개수
     */
    public int getExecutedStepCount() {
        return executedSteps.size();
    }

    /**
     * 특정 Step이 실행되었는지 확인
     *
     * @param stepName Step 이름
     * @return 실행 여부
     */
    public boolean hasExecutedStep(String stepName) {
        return executedSteps.stream()
                .anyMatch(step -> step.getName().equals(stepName));
    }

    /**
     * Context 상태 요약 (디버깅용)
     *
     * @return Context 상태 문자열
     */
    @Override
    public String toString() {
        return String.format(
                "SagaContext[userId=%d, orderItems=%d개, couponId=%s, finalAmount=%d, " +
                "inventoryDeducted=%s, balanceDeducted=%s, couponUsed=%s, orderCreated=%s, " +
                "executedSteps=%d개]",
                userId,
                orderItems != null ? orderItems.size() : 0,
                couponId,
                finalAmount,
                inventoryDeducted,
                balanceDeducted,
                couponUsed,
                orderCreated,
                executedSteps.size()
        );
    }
}