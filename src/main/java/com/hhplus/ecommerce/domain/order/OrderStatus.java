package com.hhplus.ecommerce.domain.order;

import lombok.Getter;

/**
 * OrderStatus - 도메인 값 객체 (Enum)
 *
 * 주문의 생명주기 상태를 나타냅니다.
 * - PENDING: 주문 대기 중 (현재는 사용하지 않음)
 * - COMPLETED: 주문 완료
 * - CANCELLED: 주문 취소
 *
 * 상태 전환 규칙:
 * PENDING → COMPLETED → CANCELLED
 * (역방향 전환 불가능)
 */
@Getter
public enum OrderStatus {
    PENDING("주문 대기"),
    COMPLETED("주문 완료"),
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