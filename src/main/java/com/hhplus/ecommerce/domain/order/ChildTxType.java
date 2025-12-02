package com.hhplus.ecommerce.domain.order;

/**
 * Child Transaction 타입
 *
 * 역할:
 * - 어떤 종류의 Child TX가 실행되었는지 분류
 * - 보상 로직 결정 시 사용
 *
 * 타입:
 * - BALANCE_DEDUCT: 사용자 잔액 차감 (보상: 환불)
 * - COUPON_ISSUE: 쿠폰 발급 (보상: 쿠폰 상태 복구)
 * - INVENTORY_DEDUCT: 재고 차감 (향후 확장)
 */
public enum ChildTxType {
    BALANCE_DEDUCT("사용자 잔액 차감"),
    COUPON_ISSUE("쿠폰 발급"),
    INVENTORY_DEDUCT("재고 차감");

    private final String description;

    ChildTxType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
