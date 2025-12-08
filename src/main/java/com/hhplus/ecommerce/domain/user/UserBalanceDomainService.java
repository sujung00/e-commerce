package com.hhplus.ecommerce.domain.user;

import com.hhplus.ecommerce.common.exception.ErrorCode;
import com.hhplus.ecommerce.common.exception.DomainException;

/**
 * Domain Service for User Balance-related business logic.
 * Consolidates balance validation and manipulation operations.
 * Pure business logic with no repository dependencies.
 */
public class UserBalanceDomainService {

    /**
     * Validates if a user has sufficient balance for the given amount.
     *
     * @param user the user to validate
     * @param requiredAmount the required balance in won
     * @throws DomainException if user doesn't have sufficient balance
     */
    public void validateSufficientBalance(User user, long requiredAmount) {
        if (user == null) {
            throw new DomainException(ErrorCode.USER_NOT_FOUND, "User cannot be null");
        }

        if (requiredAmount < 0) {
            throw new DomainException(
                ErrorCode.INVALID_BALANCE,
                "Required amount must be non-negative"
            );
        }

        if (user.getBalance() < requiredAmount) {
            throw new InsufficientBalanceException(
                user.getUserId(),
                user.getBalance(),
                requiredAmount
            );
        }
    }

    /**
     * Deducts the specified amount from user's balance.
     * Should be called after balance validation.
     *
     * @param user the user to deduct from
     * @param amount the amount to deduct in won
     * @throws DomainException if amount is invalid
     */
    public void deductBalance(User user, long amount) {
        if (user == null) {
            throw new DomainException(ErrorCode.USER_NOT_FOUND, "User cannot be null");
        }

        if (amount < 0) {
            throw new DomainException(
                ErrorCode.INVALID_BALANCE,
                "Deduction amount must be non-negative"
            );
        }

        long currentBalance = user.getBalance();
        long newBalance = currentBalance - amount;

        if (newBalance < 0) {
            throw new InsufficientBalanceException(
                user.getUserId(),
                currentBalance,
                amount
            );
        }

        user.setBalance(newBalance);
    }

    /**
     * Charges (adds) the specified amount to user's balance.
     *
     * @param user the user to charge
     * @param amount the amount to add in won
     * @throws DomainException if amount is invalid
     */
    public void chargeBalance(User user, long amount) {
        if (user == null) {
            throw new DomainException(ErrorCode.USER_NOT_FOUND, "User cannot be null");
        }

        if (amount < 0) {
            throw new DomainException(
                ErrorCode.INVALID_BALANCE,
                "Charge amount must be non-negative"
            );
        }

        long currentBalance = user.getBalance();
        long newBalance = currentBalance + amount;

        user.setBalance(newBalance);
    }

    /**
     * Refunds the specified amount to user's balance.
     * Semantically same as chargeBalance but with clear intent for refund operations.
     *
     * @param user the user to refund
     * @param amount the amount to refund in won
     * @throws DomainException if amount is invalid
     */
    public void refundBalance(User user, long amount) {
        if (user == null) {
            throw new DomainException(ErrorCode.USER_NOT_FOUND, "User cannot be null");
        }

        if (amount < 0) {
            throw new DomainException(
                ErrorCode.INVALID_BALANCE,
                "Refund amount must be non-negative"
            );
        }

        // Delegate to chargeBalance for actual operation
        chargeBalance(user, amount);
    }

    /**
     * Validates and deducts balance in a single atomic operation.
     * Ensures balance is sufficient before deduction.
     *
     * @param user the user to process
     * @param amount the amount to deduct in won
     * @throws DomainException if balance is insufficient
     */
    public void validateAndDeductBalance(User user, long amount) {
        validateSufficientBalance(user, amount);
        deductBalance(user, amount);
    }
}
