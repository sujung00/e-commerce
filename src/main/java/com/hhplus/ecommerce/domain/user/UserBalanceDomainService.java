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

        // Amount validity (> 0) is the responsibility of the actual operation method.
        // This method only validates that the user has sufficient balance.
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
     * Delegates to {@link User#deductBalance(Long)} which enforces:
     * - amount must be > 0 (throws {@link IllegalArgumentException} for 0 or negative)
     * - balance must be sufficient (throws {@link InsufficientBalanceException})
     * - updatedAt is refreshed on success
     *
     * @param user the user to deduct from
     * @param amount the amount to deduct in won (must be > 0)
     * @throws DomainException if user is null
     * @throws IllegalArgumentException if amount <= 0
     * @throws InsufficientBalanceException if balance is insufficient
     */
    public void deductBalance(User user, long amount) {
        if (user == null) {
            throw new DomainException(ErrorCode.USER_NOT_FOUND, "User cannot be null");
        }
        // User entity enforces amount > 0, sufficient balance, and updatedAt refresh
        user.deductBalance(amount);
    }

    /**
     * Charges (adds) the specified amount to user's balance.
     * Delegates to {@link User#chargeBalance(Long)} which enforces:
     * - amount must be > 0 (throws {@link IllegalArgumentException} for 0 or negative)
     * - updatedAt is refreshed on success
     *
     * @param user the user to charge
     * @param amount the amount to add in won (must be > 0)
     * @throws DomainException if user is null
     * @throws IllegalArgumentException if amount <= 0
     */
    public void chargeBalance(User user, long amount) {
        if (user == null) {
            throw new DomainException(ErrorCode.USER_NOT_FOUND, "User cannot be null");
        }
        // User entity enforces amount > 0 and updatedAt refresh
        user.chargeBalance(amount);
    }

    /**
     * Refunds the specified amount to user's balance.
     * Delegates to {@link User#refundBalance(Long)} which enforces:
     * - amount must be > 0 (throws {@link IllegalArgumentException} for 0 or negative)
     * - updatedAt is refreshed on success
     *
     * @param user the user to refund
     * @param amount the amount to refund in won (must be > 0)
     * @throws DomainException if user is null
     * @throws IllegalArgumentException if amount <= 0
     */
    public void refundBalance(User user, long amount) {
        if (user == null) {
            throw new DomainException(ErrorCode.USER_NOT_FOUND, "User cannot be null");
        }
        // User entity enforces amount > 0 and updatedAt refresh
        user.refundBalance(amount);
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
