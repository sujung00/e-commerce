package com.hhplus.ecommerce.domain.order;

import com.hhplus.ecommerce.common.exception.ErrorCode;
import com.hhplus.ecommerce.common.exception.DomainException;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.user.User;
import java.util.List;

/**
 * Domain Service for Order-related business logic.
 * Consolidates validation, calculation, and status update logic.
 * Pure business logic with no repository dependencies.
 */
public class OrderDomainService {

    /**
     * Validates if an order can be created with the given items, user, and coupon.
     *
     * @param user the user placing the order
     * @param orderItems the items to include in the order
     * @param coupon optional coupon for discount
     * @throws DomainException if validation fails
     */
    public void validateOrderCreation(User user, List<OrderItem> orderItems, Coupon coupon) {
        // Validate user
        if (user == null) {
            throw new DomainException(ErrorCode.USER_NOT_FOUND, "User cannot be null");
        }

        // Validate order items
        if (orderItems == null || orderItems.isEmpty()) {
            throw new DomainException(ErrorCode.INVALID_ORDER_STATUS, "Order must contain at least one item");
        }

        // Validate each item has valid quantity and unit price
        for (OrderItem item : orderItems) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new DomainException(
                    ErrorCode.INVALID_ORDER_STATUS,
                    "Order item quantity must be greater than 0"
                );
            }

            if (item.getUnitPrice() == null || item.getUnitPrice() <= 0) {
                throw new DomainException(
                    ErrorCode.INVALID_ORDER_STATUS,
                    "Order item unit price must be greater than 0"
                );
            }
        }

        // Validate coupon if provided
        if (coupon != null) {
            if (!coupon.isActiveCoupon()) {
                throw new DomainException(
                    ErrorCode.COUPON_INACTIVE,
                    "Coupon is not active"
                );
            }

            if (!coupon.hasStock()) {
                throw new DomainException(
                    ErrorCode.COUPON_OUT_OF_STOCK,
                    "Coupon has no remaining stock"
                );
            }
        }
    }

    /**
     * Calculates the order total including subtotal, discount, and final amount.
     *
     * @param orderItems the items in the order
     * @param coupon optional coupon for discount
     * @return the calculated order total
     */
    public OrderTotal calculateOrderTotal(List<OrderItem> orderItems, Coupon coupon) {
        // Calculate subtotal
        long subtotal = 0;
        for (OrderItem item : orderItems) {
            long itemTotal = item.getUnitPrice() * item.getQuantity();
            subtotal += itemTotal;
        }

        // Calculate discount
        long discountAmount = 0;
        if (coupon != null) {
            discountAmount = calculateCouponDiscount(coupon, subtotal);
        }

        // Validate discount doesn't exceed subtotal
        if (discountAmount > subtotal) {
            discountAmount = subtotal;
        }

        // Calculate final amount
        long finalAmount = subtotal - discountAmount;

        return new OrderTotal(subtotal, discountAmount, finalAmount);
    }

    /**
     * Validates if an order can be cancelled.
     *
     * @param order the order to cancel
     * @throws DomainException if validation fails
     */
    public void validateOrderCancellation(Order order) {
        if (order == null) {
            throw new DomainException(ErrorCode.ORDER_NOT_FOUND, "Order cannot be null");
        }

        if (!order.isCancellable()) {
            throw new DomainException(
                ErrorCode.INVALID_ORDER_STATUS,
                "Order in status " + order.getOrderStatus() + " cannot be cancelled"
            );
        }
    }

    /**
     * Updates product statuses after order placement.
     * This method should be called after order is successfully persisted.
     *
     * @param order the created order
     * @param products the products that were ordered
     */
    public void updateProductStatusAfterOrder(Order order, List<Product> products) {
        if (order == null || products == null || products.isEmpty()) {
            return;
        }

        for (Product product : products) {
            product.recalculateTotalStock();
        }
    }

    /**
     * Calculates coupon discount amount based on coupon type.
     * Supports FIXED_AMOUNT (fixed discount) and PERCENTAGE (percentage discount).
     *
     * @param coupon the coupon providing discount
     * @param subtotal the order subtotal in won
     * @return the discount amount
     */
    private long calculateCouponDiscount(Coupon coupon, long subtotal) {
        if (coupon == null) {
            return 0;
        }

        // Determine discount based on coupon type
        String discountType = coupon.getDiscountType();
        if ("PERCENTAGE".equals(discountType)) {
            // discountRate is BigDecimal, convert to double for calculation
            double rateAsDouble = coupon.getDiscountRate().doubleValue();
            return (long) (subtotal * rateAsDouble);
        } else if ("FIXED_AMOUNT".equals(discountType)) {
            return Math.max(0, coupon.getDiscountAmount());
        }

        return 0;
    }

    /**
     * Value object for calculated order total.
     */
    public static class OrderTotal {
        private final long subtotal;
        private final long discountAmount;
        private final long finalAmount;

        public OrderTotal(long subtotal, long discountAmount, long finalAmount) {
            this.subtotal = subtotal;
            this.discountAmount = discountAmount;
            this.finalAmount = finalAmount;
        }

        public long getSubtotal() {
            return subtotal;
        }

        public long getDiscountAmount() {
            return discountAmount;
        }

        public long getFinalAmount() {
            return finalAmount;
        }
    }
}
