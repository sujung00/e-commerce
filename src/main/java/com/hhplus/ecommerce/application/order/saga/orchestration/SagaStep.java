package com.hhplus.ecommerce.application.order.saga.orchestration;

import com.hhplus.ecommerce.application.order.saga.context.SagaContext;

/**
 * SagaStep - Saga 오케스트레이터 패턴의 개별 Step 인터페이스
 *
 * 역할:
 * - Saga 워크플로우의 각 단계를 정의하는 공통 인터페이스
 * - Forward Flow: execute() 메서드로 정상 플로우 처리
 * - Backward Flow: compensate() 메서드로 보상 트랜잭션 처리
 *
 * 구현 규칙:
 * - 각 Step은 독립적으로 실행 가능해야 함
 * - 실행 순서는 getOrder()로 정의
 * - 보상 순서는 실행 순서의 역순 (LIFO)
 * - 멱등성(Idempotency): 동일 요청 재실행 시 동일 결과 보장
 * - SagaContext를 통해 Step 간 데이터 공유
 *
 * 주요 메서드:
 * - getName(): Step 식별자 (로깅 및 디버깅용)
 * - execute(): Step 실행 로직 (정상 플로우)
 * - compensate(): Step 보상 로직 (롤백 플로우)
 * - getOrder(): Step 실행 순서 (숫자가 작을수록 먼저 실행)
 *
 * 실행 순서:
 * 1. DeductInventoryStep (order=1): 재고 차감
 * 2. DeductBalanceStep (order=2): 포인트 차감
 * 3. UseCouponStep (order=3): 쿠폰 사용 처리
 * 4. CreateOrderStep (order=4): 주문 생성
 *
 * 보상 순서 (역순 LIFO):
 * 4. CreateOrderStep: 주문 취소
 * 3. UseCouponStep: 쿠폰 복구
 * 2. DeductBalanceStep: 포인트 환불
 * 1. DeductInventoryStep: 재고 복구
 */
public interface SagaStep {

    /**
     * Step 이름 반환 (로깅 및 디버깅용)
     *
     * @return Step 식별자 (예: "DeductInventoryStep")
     */
    String getName();

    /**
     * Step 실행 (Forward Flow)
     *
     * 역할:
     * - 각 Step의 핵심 비즈니스 로직 실행
     * - SagaContext에 실행 결과 및 메타데이터 저장
     * - 예외 발생 시 상위로 전파 (Orchestrator가 보상 플로우 시작)
     *
     * @param context Saga 실행 컨텍스트 (공유 데이터)
     * @throws Exception Step 실행 중 오류 발생 시
     */
    void execute(SagaContext context) throws Exception;

    /**
     * Step 보상 (Backward Flow / Rollback)
     *
     * 역할:
     * - execute()에서 수행한 작업을 되돌림 (롤백)
     * - SagaContext의 보상 플래그를 확인하여 실행 여부 결정
     * - 보상 실패 시 예외를 발생시키지 말고 로깅만 수행 (Best Effort)
     *
     * 보상 로직 패턴:
     * 1. 보상 플래그 확인 (예: context.isInventoryDeducted())
     * 2. 플래그가 true이면 보상 실행, false이면 skip
     * 3. 보상 실패 시 로깅만 하고 예외 전파하지 않음
     *
     * @param context Saga 실행 컨텍스트 (보상에 필요한 메타데이터 포함)
     * @throws Exception 보상 중 치명적 오류 발생 시 (선택적)
     */
    void compensate(SagaContext context) throws Exception;

    /**
     * Step 실행 순서 반환
     *
     * 역할:
     * - Orchestrator가 Step들을 정렬하여 순차 실행하는 데 사용
     * - 숫자가 작을수록 먼저 실행
     * - 보상 순서는 자동으로 역순(LIFO)
     *
     * 기본값: 0 (순서 지정 안 함)
     *
     * @return 실행 순서 (1, 2, 3, 4...)
     */
    default int getOrder() {
        return 0;
    }
}