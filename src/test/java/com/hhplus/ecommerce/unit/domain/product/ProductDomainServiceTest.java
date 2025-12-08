package com.hhplus.ecommerce.unit.domain.product;

import com.hhplus.ecommerce.common.exception.ErrorCode;
import com.hhplus.ecommerce.common.exception.DomainException;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductDomainService;
import com.hhplus.ecommerce.domain.product.ProductOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProductDomainService - 상품 도메인 비즈니스 로직")
class ProductDomainServiceTest {

    private ProductDomainService productDomainService;

    @BeforeEach
    void setUp() {
        productDomainService = new ProductDomainService();
    }

    // ==================== validateOptionStock Tests ====================

    @Test
    @DisplayName("옵션 재고 검증 - 충분한 재고")
    void validateOptionStock_WithSufficientStock_Success() {
        // Given
        ProductOption option = createTestProductOption(1L, 10);

        // When & Then
        assertDoesNotThrow(() -> productDomainService.validateOptionStock(option, 5));
    }

    @Test
    @DisplayName("옵션 재고 검증 - 정확히 같은 수량")
    void validateOptionStock_WithExactQuantity_Success() {
        // Given
        ProductOption option = createTestProductOption(1L, 10);

        // When & Then
        assertDoesNotThrow(() -> productDomainService.validateOptionStock(option, 10));
    }

    @Test
    @DisplayName("옵션 재고 검증 - null 옵션")
    void validateOptionStock_WithNullOption_ThrowsException() {
        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> productDomainService.validateOptionStock(null, 5));
        assertEquals(ErrorCode.INVALID_QUANTITY, exception.getErrorCode());
    }

    @Test
    @DisplayName("옵션 재고 검증 - 부족한 재고")
    void validateOptionStock_WithInsufficientStock_ThrowsException() {
        // Given
        ProductOption option = createTestProductOption(1L, 5);

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> productDomainService.validateOptionStock(option, 10));
        assertEquals(ErrorCode.INSUFFICIENT_STOCK, exception.getErrorCode());
    }

    @Test
    @DisplayName("옵션 재고 검증 - 0 요청 수량")
    void validateOptionStock_WithZeroQuantity_ThrowsException() {
        // Given
        ProductOption option = createTestProductOption(1L, 10);

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> productDomainService.validateOptionStock(option, 0));
        assertEquals(ErrorCode.INVALID_QUANTITY, exception.getErrorCode());
    }

    @Test
    @DisplayName("옵션 재고 검증 - 음수 요청 수량")
    void validateOptionStock_WithNegativeQuantity_ThrowsException() {
        // Given
        ProductOption option = createTestProductOption(1L, 10);

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> productDomainService.validateOptionStock(option, -5));
        assertEquals(ErrorCode.INVALID_QUANTITY, exception.getErrorCode());
    }

    @Test
    @DisplayName("옵션 재고 검증 - 0 재고")
    void validateOptionStock_WithZeroStock_ThrowsException() {
        // Given
        ProductOption option = createTestProductOption(1L, 0);

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> productDomainService.validateOptionStock(option, 1));
        assertEquals(ErrorCode.INSUFFICIENT_STOCK, exception.getErrorCode());
    }

    // ==================== validateProductAvailableForDeduction Tests ====================

    @Test
    @DisplayName("상품 차감 가능 검증 - 정상 케이스")
    void validateProductAvailableForDeduction_WithAvailableProduct_Success() {
        // Given
        Product product = createTestProduct(1L, "IN_STOCK", 100);

        // When & Then
        assertDoesNotThrow(() -> productDomainService.validateProductAvailableForDeduction(product));
    }

    @Test
    @DisplayName("상품 차감 가능 검증 - null 상품")
    void validateProductAvailableForDeduction_WithNullProduct_ThrowsException() {
        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> productDomainService.validateProductAvailableForDeduction(null));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("상품 차감 가능 검증 - 품절 상품")
    void validateProductAvailableForDeduction_WithSoldOutProduct_ThrowsException() {
        // Given
        Product product = createTestProduct(1L, "SOLD_OUT", 0);

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> productDomainService.validateProductAvailableForDeduction(product));
        assertEquals(ErrorCode.INSUFFICIENT_STOCK, exception.getErrorCode());
    }

    @Test
    @DisplayName("상품 차감 가능 검증 - 0 재고")
    void validateProductAvailableForDeduction_WithZeroStock_ThrowsException() {
        // Given
        Product product = createTestProduct(1L, "IN_STOCK", 0);

        // When & Then
        DomainException exception = assertThrows(DomainException.class,
                () -> productDomainService.validateProductAvailableForDeduction(product));
        assertEquals(ErrorCode.INSUFFICIENT_STOCK, exception.getErrorCode());
    }

    // ==================== updateStatusAfterStockDeduction Tests ====================

    @Test
    @DisplayName("재고 차감 후 상태 업데이트 - null 상품")
    void updateStatusAfterStockDeduction_WithNullProduct_DoesNotThrow() {
        // When & Then
        assertDoesNotThrow(() -> productDomainService.updateStatusAfterStockDeduction(null));
    }

    @Test
    @DisplayName("재고 차감 후 상태 업데이트 - 재고 있음")
    void updateStatusAfterStockDeduction_WithStock_UpdatesStatus() {
        // Given
        Product product = createTestProduct(1L, "IN_STOCK", 100);
        ProductOption option = createTestProductOption(1L, 100);
        product.addOption(option);

        // When
        productDomainService.updateStatusAfterStockDeduction(product);

        // Then
        assertEquals("IN_STOCK", product.getStatus());
        assertEquals(100, product.getTotalStock());
    }

    @Test
    @DisplayName("재고 차감 후 상태 업데이트 - 재고 없음")
    void updateStatusAfterStockDeduction_WithoutStock_UpdatesToSoldOut() {
        // Given
        Product product = createTestProduct(1L, "IN_STOCK", 0);
        // Don't add any options, total stock = 0

        // When
        productDomainService.updateStatusAfterStockDeduction(product);

        // Then
        assertEquals("SOLD_OUT", product.getStatus());
        assertEquals(0, product.getTotalStock());
    }

    // ==================== Complex Scenarios ====================

    @Test
    @DisplayName("복합 시나리오 - 여러 옵션의 재고 검증")
    void complexScenario_MultipleOptions_ValidatesCorrectly() {
        // Given
        ProductOption option1 = createTestProductOption(1L, 10);
        ProductOption option2 = createTestProductOption(2L, 5);

        // When & Then
        assertDoesNotThrow(() -> {
            productDomainService.validateOptionStock(option1, 10);
            productDomainService.validateOptionStock(option2, 5);
        });
    }

    @Test
    @DisplayName("복합 시나리오 - 옵션 하나는 재고 부족")
    void complexScenario_OneOptionInsufficientStock() {
        // Given
        ProductOption option1 = createTestProductOption(1L, 10);
        ProductOption option2 = createTestProductOption(2L, 2); // 부족

        // When & Then - 첫 번째 옵션은 OK
        assertDoesNotThrow(() -> productDomainService.validateOptionStock(option1, 10));

        // 두 번째 옵션은 실패
        DomainException exception = assertThrows(DomainException.class,
                () -> productDomainService.validateOptionStock(option2, 5));
        assertEquals(ErrorCode.INSUFFICIENT_STOCK, exception.getErrorCode());
    }

    @Test
    @DisplayName("복합 시나리오 - 상품 상태 업데이트 플로우")
    void complexScenario_ProductStatusUpdateFlow() {
        // Given
        Product product = createTestProduct(1L, "IN_STOCK", 50);
        ProductOption option = createTestProductOption(1L, 50);
        product.addOption(option);

        // Verify initial state
        assertTrue(product.isAvailable());
        assertEquals("IN_STOCK", product.getStatus());

        // When - validate and update
        assertDoesNotThrow(() -> productDomainService.validateProductAvailableForDeduction(product));
        productDomainService.updateStatusAfterStockDeduction(product);

        // Then - status should remain IN_STOCK since we haven't actually deducted stock
        assertEquals("IN_STOCK", product.getStatus());
    }

    // ==================== Helper Methods ====================

    private Product createTestProduct(Long productId, String status, int totalStock) {
        return Product.builder()
                .productId(productId)
                .productName("Test Product " + productId)
                .description("Test Description")
                .price(10000L)
                .totalStock(totalStock)
                .status(status)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private ProductOption createTestProductOption(Long optionId, int stock) {
        return ProductOption.builder()
                .optionId(optionId)
                .productId(1L)
                .name("Option " + optionId)
                .stock(stock)
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
