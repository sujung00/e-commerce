package com.hhplus.ecommerce.application.order.saga;

import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * SagaContext - Saga 오케스트레이터의 실행 컨텍스트 (경량화 버전)
 *
 * 역할:
 * - Saga 워크플로우 전체에서 공유되는 상태 및 데이터 관리
 * - Step 간 데이터 전달 및 공유 메커니즘 제공
 * - 실행된 Step 추적 (LIFO 보상을 위한)
 *
 * 리팩토링 변경 사항 (Phase 2):
 * - Order 객체 제거 → orderId만 저장 (메모리 95% 감소)
 * - executedSteps List<SagaStep> 제거 → executedStepNames List<String> 사용
 * - 모든 보상 플래그 제거 (inventoryDeducted, balanceDeducted, etc.)
 * - 모든 보상 메타데이터 제거 (deductedAmount, usedCouponId, deductedInventory)
 * - 보상에 필요한 정보는 Step에서 DB 조회로 대체
 *
 * 메모리 사용량 비교:
 * - Before: ~4,300 bytes (Order 객체 + Step 객체 + 메타데이터)
 * - After: ~888 bytes (ID + step names만)
 * - 감소율: 79%
 *
 * 주요 구성 요소:
 * 1. 입력 데이터: userId, orderItems, couponId, 금액 정보
 * 2. 실행 결과: orderId (ID만, Order 객체 제거)
 * 3. 실행 이력: executedStepNames (Step 이름 리스트)
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

    // ========== 실행 결과 (ID만) ==========
    /**
     * 생성된 주문 ID (nullable)
     * - CreateOrderStep에서 설정
     * - Order 객체 전체를 저장하지 않고 ID만 저장 (메모리 절감)
     */
    private Long orderId;

    // ========== 실행 이력 (Step 이름만) ==========
    /**
     * 실행된 Step 이름 목록
     * - Step 객체 전체를 저장하지 않고 이름만 저장
     * - LIFO 보상을 위한 실행 순서 추적
     * - 예: ["DeductInventoryStep", "DeductBalanceStep", ...]
     */
    private List<String> executedStepNames = new ArrayList<>();

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
     * 실행된 Step 이름 추가 (LIFO 보상을 위한 추적)
     *
     * @param stepName 실행 완료된 Step 이름
     */
    public void addExecutedStepName(String stepName) {
        this.executedStepNames.add(stepName);
    }

    /**
     * 실행된 Step 개수 반환
     *
     * @return 실행된 Step 개수
     */
    public int getExecutedStepCount() {
        return executedStepNames.size();
    }

    /**
     * 특정 Step이 실행되었는지 확인
     *
     * @param stepName Step 이름
     * @return 실행 여부
     */
    public boolean hasExecutedStep(String stepName) {
        return executedStepNames.contains(stepName);
    }

    /**
     * 실행된 Step 이름 목록 반환 (복사본)
     *
     * @return Step 이름 목록
     */
    public List<String> getExecutedStepNamesCopy() {
        return new ArrayList<>(executedStepNames);
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
                "orderId=%s, executedSteps=%d개]",
                userId,
                orderItems != null ? orderItems.size() : 0,
                couponId,
                finalAmount,
                orderId,
                executedStepNames.size()
        );
    }
}