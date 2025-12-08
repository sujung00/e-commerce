package com.hhplus.ecommerce.domain.product;

import com.hhplus.ecommerce.common.exception.ErrorCode;
import com.hhplus.ecommerce.common.exception.DomainException;

/**
 * Domain Service for Product-related business logic.
 * Consolidates stock validation and availability checks.
 * Pure business logic with no repository dependencies.
 */
public class ProductDomainService {

    /**
     * Validates if a product option has sufficient stock for the requested quantity.
     *
     * @param productOption the product option to validate
     * @param requestedQuantity the quantity requested
     * @throws DomainException if validation fails
     */
    public void validateOptionStock(ProductOption productOption, int requestedQuantity) {
        if (productOption == null) {
            throw new DomainException(
                ErrorCode.INVALID_QUANTITY,
                "Product option cannot be null"
            );
        }

        if (requestedQuantity <= 0) {
            throw new DomainException(
                ErrorCode.INVALID_QUANTITY,
                "Requested quantity must be greater than 0"
            );
        }

        long availableStock = productOption.getStock();
        if (availableStock < requestedQuantity) {
            throw new DomainException(
                ErrorCode.INSUFFICIENT_STOCK,
                "Insufficient stock. Available: " + availableStock +
                    ", Requested: " + requestedQuantity
            );
        }
    }

    /**
     * Validates if a product is available for stock deduction.
     * A product is available if it has IN_STOCK status.
     *
     * @param product the product to validate
     * @throws DomainException if validation fails
     */
    public void validateProductAvailableForDeduction(Product product) {
        if (product == null) {
            throw new DomainException(ErrorCode.PRODUCT_NOT_FOUND, "Product cannot be null");
        }

        if (!product.isAvailable()) {
            throw new DomainException(
                ErrorCode.INSUFFICIENT_STOCK,
                "Product is not available for purchase"
            );
        }
    }

    /**
     * Updates product status after stock deduction.
     * Recalculates total stock and updates product status based on availability.
     * Should be called after successfully deducting stock from product options.
     *
     * @param product the product to update
     */
    public void updateStatusAfterStockDeduction(Product product) {
        if (product == null) {
            return;
        }

        // Recalculate total stock across all options
        // This method automatically updates status based on inventory
        product.recalculateTotalStock();
    }

}
