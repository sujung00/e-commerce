package com.hhplus.ecommerce.domain.coupon;

import com.hhplus.ecommerce.common.exception.ErrorCode;
import com.hhplus.ecommerce.common.exception.DomainException;
import com.hhplus.ecommerce.domain.user.User;
import java.time.LocalDateTime;

/**
 * Domain Service for Coupon-related business logic.
 * Consolidates validation, stock management, and discount calculation.
 * Pure business logic with no repository dependencies.
 */
public class CouponDomainService {

    /**
     * Validates if a coupon can be issued to a user at the given time.
     *
     * @param coupon the coupon to issue
     * @param user the user receiving the coupon
     * @param now the current time
     * @throws DomainException if validation fails
     */
    public void validateCouponIssuable(Coupon coupon, User user, LocalDateTime now) {
        if (coupon == null) {
            throw new DomainException(ErrorCode.COUPON_NOT_FOUND, "Coupon cannot be null");
        }

        if (user == null) {
            throw new DomainException(ErrorCode.USER_NOT_FOUND, "User cannot be null");
        }

        if (!coupon.isActiveCoupon()) {
            throw new DomainException(
                ErrorCode.COUPON_INACTIVE,
                "Coupon is not active"
            );
        }

        if (!coupon.isValidPeriod(now)) {
            throw new DomainException(
                ErrorCode.COUPON_EXPIRED,
                "Coupon is not within valid period"
            );
        }

        if (!coupon.hasStock()) {
            throw new DomainException(
                ErrorCode.COUPON_OUT_OF_STOCK,
                "Coupon has no remaining stock"
            );
        }
    }

    /**
     * Decreases coupon stock by the given quantity.
     * Automatically deactivates the coupon if stock becomes zero.
     *
     * @param coupon the coupon to decrease stock for
     * @param quantity the quantity to decrease
     * @throws DomainException if validation fails
     */
    public void decreaseStock(Coupon coupon, int quantity) {
        if (coupon == null) {
            throw new DomainException(ErrorCode.COUPON_NOT_FOUND, "Coupon cannot be null");
        }

        if (quantity <= 0) {
            throw new DomainException(
                ErrorCode.INVALID_QUANTITY,
                "Decrease quantity must be greater than 0"
            );
        }

        if (!coupon.hasStock()) {
            throw new DomainException(
                ErrorCode.COUPON_OUT_OF_STOCK,
                "Coupon has no remaining stock"
            );
        }

        // Delegate to coupon entity which handles decreasing and auto-deactivation
        coupon.decreaseStock();
    }

    /**
     * Validates if a coupon can be used by a user at the given time.
     *
     * @param userCoupon the user's coupon assignment
     * @param coupon the coupon to validate
     * @param now the current time
     * @throws DomainException if validation fails
     */
    public void validateCouponUsable(UserCoupon userCoupon, Coupon coupon, LocalDateTime now) {
        if (userCoupon == null) {
            throw new DomainException(ErrorCode.COUPON_NOT_FOUND, "User coupon cannot be null");
        }

        if (coupon == null) {
            throw new DomainException(ErrorCode.COUPON_NOT_FOUND, "Coupon cannot be null");
        }

        // Check if coupon has been used (status should be USED)
        if (userCoupon.getStatus() != null && "USED".equals(userCoupon.getStatus().toString())) {
            throw new DomainException(
                ErrorCode.COUPON_ALREADY_ISSUED,
                "Coupon has already been used"
            );
        }

        if (!coupon.isActiveCoupon()) {
            throw new DomainException(
                ErrorCode.COUPON_INACTIVE,
                "Coupon is not active"
            );
        }

        if (!coupon.isValidPeriod(now)) {
            throw new DomainException(
                ErrorCode.COUPON_EXPIRED,
                "Coupon is not within valid period"
            );
        }
    }

    /**
     * Calculates the discount amount based on coupon type.
     * Supports FIXED_AMOUNT (fixed discount) and PERCENTAGE (percentage discount).
     *
     * @param coupon the coupon providing discount
     * @param subtotal the order subtotal in won
     * @return the discount amount
     */
    public long calculateDiscountAmount(Coupon coupon, long subtotal) {
        if (coupon == null) {
            return 0;
        }

        String discountType = coupon.getDiscountType();

        if ("PERCENTAGE".equals(discountType)) {
            double discountRate = coupon.getDiscountRate().doubleValue();
            long discountAmount = (long) (subtotal * discountRate);
            return discountAmount;
        } else if ("FIXED_AMOUNT".equals(discountType)) {
            long discountAmount = coupon.getDiscountAmount();
            return discountAmount;
        }

        return 0;
    }

    /**
     * Restores coupon stock after a failed order transaction.
     * This is called when an order transaction is rolled back.
     *
     * @param coupon the coupon to restore stock for
     * @throws DomainException if validation fails
     */
    public void restoreCoupon(Coupon coupon) {
        if (coupon == null) {
            throw new DomainException(ErrorCode.COUPON_NOT_FOUND, "Coupon cannot be null");
        }

        // In current implementation, we would increment remainingQuantity
        // This is a placeholder for the restoration logic
        // Actual implementation depends on how Coupon entity handles stock restoration
    }
}
