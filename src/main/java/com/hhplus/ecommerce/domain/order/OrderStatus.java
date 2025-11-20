package com.hhplus.ecommerce.domain.order;

import lombok.Getter;

/**
 * OrderStatus - 도메인 값 객체 (Enum)
 *
 * 주문의 생명주기 상태를 나타냅니다.
 * - PENDING: 주문 생성됨, 결제 대기 중
 * - PAID: 결제 완료
 * - COMPLETED: 주문 완료 (배송 등 후속 처리 완료)
 * - CANCELLED: 주문 취소 또는 결제 실패로 인한 보상 처리 완료
 * - FAILED: 결제 실패 (보상 처리 필요)
 *
 * 상태 전환 규칙:
 * PENDING → PAID → COMPLETED
 * PENDING → FAILED → CANCELLED
 * COMPLETED → CANCELLED (취소 요청 시)
 */
@Getter
public enum OrderStatus {
    PENDING("주문 대기"),
    PAID("결제 완료"),
    COMPLETED("주문 완료"),
    FAILED("결제 실패"),
    CANCELLED("주문 취소");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 문자열에서 OrderStatus로 변환
     */
    public static OrderStatus fromString(String status) {
        try {
            return OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 주문 상태입니다: " + status);
        }
    }
}