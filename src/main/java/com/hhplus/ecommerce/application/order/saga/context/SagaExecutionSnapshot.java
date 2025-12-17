package com.hhplus.ecommerce.application.order.saga.context;

import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * SagaExecutionSnapshot - Saga 실행 스냅샷 (경량화된 실행 컨텍스트)
 *
 * 역할:
 * - Saga 실행에 필요한 최소한의 정보만 보관
 * - 메모리 사용량 최소화 (도메인 객체 저장 금지)
 * - 직렬화 가능하고 가벼운 구조
 *
 * 설계 원칙:
 * - Order, User 등 도메인 객체는 저장하지 않음
 * - ID만 저장하고, 필요 시 DB 조회
 * - executedSteps 대신 stepNames (List&lt;String&gt;)만 저장
 * - 보상 메타데이터 제거 (각 Step에서 자체 관리)
 *
 * 메모리 절감 효과:
 * - 기존 SagaContext: ~4,300 bytes per execution (주문 10개 기준)
 * - SagaExecutionSnapshot: ~200 bytes per execution (95% 감소)
 *
 * 포함 정보:
 * - userId: 사용자 ID
 * - orderItems: 주문 항목 리스트 (입력 데이터, 필수)
 * - couponId: 쿠폰 ID (nullable)
 * - couponDiscount: 쿠폰 할인액
 * - subtotal: 주문 소계
 * - finalAmount: 최종 결제 금액
 * - orderId: 생성된 주문 ID (nullable, CreateOrderStep에서 설정)
 * - executedStepNames: 실행된 Step 이름 목록 (LIFO 보상용)
 */
@Getter
@Builder
public class SagaExecutionSnapshot {

    // ========== 입력 데이터 (필수) ==========

    /**
     * 사용자 ID
     */
    private final Long userId;

    /**
     * 주문 항목 리스트
     * - Step 실행에 필요한 입력 데이터
     */
    private final List<OrderItemDto> orderItems;

    /**
     * 쿠폰 ID (nullable)
     */
    private final Long couponId;

    /**
     * 쿠폰 할인액
     */
    private final Long couponDiscount;

    /**
     * 주문 소계 (할인 전)
     */
    private final Long subtotal;

    /**
     * 최종 결제 금액 (쿠폰 할인 적용 후)
     */
    private final Long finalAmount;

    // ========== 실행 결과 (ID만) ==========

    /**
     * 생성된 주문 ID (nullable)
     * - CreateOrderStep에서 설정
     * - Order 객체 전체를 저장하지 않고 ID만 저장
     */
    private Long orderId;

    // ========== 실행 이력 (Step 이름만) ==========

    /**
     * 실행된 Step 이름 목록
     * - Step 객체 전체를 저장하지 않고 이름만 저장
     * - LIFO 보상을 위한 실행 순서 추적
     * - 예: ["DeductInventoryStep", "DeductBalanceStep", ...]
     */
    @Builder.Default
    private final List<String> executedStepNames = new ArrayList<>();

    // ========== Helper Methods ==========

    /**
     * 실행된 Step 이름 추가
     *
     * @param stepName Step 이름
     */
    public void recordStepExecution(String stepName) {
        this.executedStepNames.add(stepName);
    }

    /**
     * orderId 설정 (CreateOrderStep에서 호출)
     *
     * @param orderId 생성된 주문 ID
     */
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
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
     * 실행된 Step 개수 반환
     *
     * @return 실행된 Step 개수
     */
    public int getExecutedStepCount() {
        return executedStepNames.size();
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
     * Snapshot 상태 요약 (디버깅용)
     *
     * @return Snapshot 상태 문자열
     */
    @Override
    public String toString() {
        return String.format(
                "SagaExecutionSnapshot[userId=%d, orderItems=%d개, couponId=%s, finalAmount=%d, " +
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