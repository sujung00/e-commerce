package com.hhplus.ecommerce.domain.order.event;

import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Order Saga 통합 이벤트
 *
 * 역할:
 * - Order Saga 실행 결과를 표현하는 단일 통합 이벤트
 * - 기존에 흩어져 있던 Saga 관련 이벤트들을 대체
 *
 * 통합된 기존 이벤트:
 * - PaymentSuccessEvent → OrderSagaEvent(COMPLETED)
 * - CompensationCompletedEvent → (제거, FAILED에 포함)
 * - CompensationFailedEvent → OrderSagaEvent(COMPENSATION_FAILED)
 *
 * 설계 원칙:
 * - 이벤트는 "무슨 일이 일어났는가"만 표현
 * - sagaEventType으로 이벤트 종류 구분
 * - 리스너에서 타입별로 분기 처리
 *
 * 확장성:
 * - SagaEventType enum에 새로운 타입 추가 가능
 * - 필드 추가로 더 많은 정보 전달 가능
 */
@Getter
@ToString
public class OrderSagaEvent {

    /**
     * 주문 ID (nullable: Saga 실행 전 실패 시 null 가능)
     */
    private final Long orderId;

    /**
     * 사용자 ID
     */
    private final Long userId;

    /**
     * Saga 이벤트 타입 (COMPLETED, FAILED, COMPENSATION_FAILED)
     */
    private final SagaEventType sagaEventType;

    /**
     * 이벤트 발생 시점
     */
    private final LocalDateTime occurredAt;

    /**
     * 최종 결제 금액 (optional: COMPLETED 시 사용)
     */
    private final Long finalAmount;

    /**
     * 에러 메시지 (optional: FAILED, COMPENSATION_FAILED 시 사용)
     */
    private final String errorMessage;

    /**
     * OrderSagaEvent 생성 (기본 생성자)
     *
     * @param orderId 주문 ID (nullable)
     * @param userId 사용자 ID
     * @param sagaEventType Saga 이벤트 타입
     * @param finalAmount 최종 결제 금액 (optional)
     * @param errorMessage 에러 메시지 (optional)
     */
    public OrderSagaEvent(Long orderId,
                          Long userId,
                          SagaEventType sagaEventType,
                          Long finalAmount,
                          String errorMessage) {
        this.orderId = orderId;
        this.userId = userId;
        this.sagaEventType = sagaEventType;
        this.occurredAt = LocalDateTime.now();
        this.finalAmount = finalAmount;
        this.errorMessage = errorMessage;
    }

    /**
     * Saga 성공 이벤트 생성 (COMPLETED)
     *
     * 사용 예:
     * OrderSagaEvent.completed(orderId, userId, finalAmount)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param finalAmount 최종 결제 금액
     * @return OrderSagaEvent (COMPLETED)
     */
    public static OrderSagaEvent completed(Long orderId, Long userId, Long finalAmount) {
        return new OrderSagaEvent(
                orderId,
                userId,
                SagaEventType.COMPLETED,
                finalAmount,
                null
        );
    }

    /**
     * Saga 실패 이벤트 생성 (FAILED)
     *
     * 사용 예:
     * OrderSagaEvent.failed(orderId, userId, "재고 부족")
     *
     * @param orderId 주문 ID (nullable)
     * @param userId 사용자 ID
     * @param errorMessage 실패 원인 메시지
     * @return OrderSagaEvent (FAILED)
     */
    public static OrderSagaEvent failed(Long orderId, Long userId, String errorMessage) {
        return new OrderSagaEvent(
                orderId,
                userId,
                SagaEventType.FAILED,
                null,
                errorMessage
        );
    }

    /**
     * 보상 실패 이벤트 생성 (COMPENSATION_FAILED)
     *
     * 사용 예:
     * OrderSagaEvent.compensationFailed(orderId, userId, "재고 복구 실패")
     *
     * @param orderId 주문 ID (nullable)
     * @param userId 사용자 ID
     * @param errorMessage 보상 실패 원인 메시지
     * @return OrderSagaEvent (COMPENSATION_FAILED)
     */
    public static OrderSagaEvent compensationFailed(Long orderId, Long userId, String errorMessage) {
        return new OrderSagaEvent(
                orderId,
                userId,
                SagaEventType.COMPENSATION_FAILED,
                null,
                errorMessage
        );
    }
}