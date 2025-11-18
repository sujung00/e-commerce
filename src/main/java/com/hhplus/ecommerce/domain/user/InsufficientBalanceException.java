package com.hhplus.ecommerce.domain.user;

/**
 * InsufficientBalanceException - 잔액 부족 예외
 *
 * 사용자가 출금(결제)하려는 금액이 현재 잔액보다 클 때 발생합니다.
 */
public class InsufficientBalanceException extends RuntimeException {
    private final Long userId;
    private final Long currentBalance;
    private final Long requiredAmount;

    public InsufficientBalanceException(Long userId, Long currentBalance, Long requiredAmount) {
        super(String.format(
                "잔액 부족: 사용자=%d, 보유잔액=%d, 필요금액=%d",
                userId, currentBalance, requiredAmount
        ));
        this.userId = userId;
        this.currentBalance = currentBalance;
        this.requiredAmount = requiredAmount;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCurrentBalance() {
        return currentBalance;
    }

    public Long getRequiredAmount() {
        return requiredAmount;
    }

    public Long getShortfall() {
        return requiredAmount - currentBalance;
    }
}