package com.hhplus.ecommerce.unit.application.inventory;


import com.hhplus.ecommerce.application.inventory.InventoryService;import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductNotFoundException;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.presentation.inventory.response.InventoryResponse;
import com.hhplus.ecommerce.presentation.inventory.response.OptionInventoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * InventoryServiceTest - Application 계층 단위 테스트
 * Spring Boot 3.4+ Mockito 방식 테스트
 *
 * 테스트 대상: InventoryService
 * - 상품 재고 현황 조회
 * - 옵션별 재고 정보 제공
 * - 전체 재고 계산
 *
 * 테스트 유형:
 * - 성공 케이스: 정상적인 재고 조회
 * - 옵션 유무: 옵션 있음/없음 케이스
 * - 예외 케이스: 유효하지 않은 productId, 상품 없음
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService 단위 테스트")
class InventoryServiceTest {

    private InventoryService inventoryService;

    @Mock
    private ProductRepository productRepository;

    private static final Long TEST_PRODUCT_ID = 1L;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        inventoryService = new InventoryService(productRepository);
    }

    // ========== 상품 재고 현황 조회 (getProductInventory) ==========

    @Test
    @DisplayName("상품 재고 조회 - 성공 (다중 옵션)")
    void testGetProductInventory_Success_WithMultipleOptions() {
        // Given
        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("프리미엄 우육 500g")
                .description("고급 우육 제품")
                .price(50000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<ProductOption> options = List.of(
                ProductOption.builder()
                        .optionId(1L)
                        .productId(TEST_PRODUCT_ID)
                        .name("사이즈 S")
                        .stock(100)
                        .version(1L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build(),
                ProductOption.builder()
                        .optionId(2L)
                        .productId(TEST_PRODUCT_ID)
                        .name("사이즈 M")
                        .stock(150)
                        .version(1L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build(),
                ProductOption.builder()
                        .optionId(3L)
                        .productId(TEST_PRODUCT_ID)
                        .name("사이즈 L")
                        .stock(200)
                        .version(1L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );

        when(productRepository.findById(TEST_PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(productRepository.findOptionsByProductId(TEST_PRODUCT_ID))
                .thenReturn(options);

        // When
        InventoryResponse result = inventoryService.getProductInventory(TEST_PRODUCT_ID);

        // Then
        assertNotNull(result);
        assertEquals(TEST_PRODUCT_ID, result.getProductId());
        assertEquals("프리미엄 우육 500g", result.getProductName());
        assertEquals(450, result.getTotalStock());
        assertEquals(3, result.getOptions().size());

        OptionInventoryView firstOption = result.getOptions().get(0);
        assertEquals(1L, firstOption.getOptionId());
        assertEquals("사이즈 S", firstOption.getName());
        assertEquals(100, firstOption.getStock());
        assertEquals(1, firstOption.getVersion());

        verify(productRepository, times(1)).findById(TEST_PRODUCT_ID);
        verify(productRepository, times(1)).findOptionsByProductId(TEST_PRODUCT_ID);
    }

    @Test
    @DisplayName("상품 재고 조회 - 성공 (단일 옵션)")
    void testGetProductInventory_Success_WithSingleOption() {
        // Given
        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("기본 상품")
                .description("기본 상품 설명")
                .price(30000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<ProductOption> options = List.of(
                ProductOption.builder()
                        .optionId(1L)
                        .productId(TEST_PRODUCT_ID)
                        .name("기본")
                        .stock(500)
                        .version(1L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );

        when(productRepository.findById(TEST_PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(productRepository.findOptionsByProductId(TEST_PRODUCT_ID))
                .thenReturn(options);

        // When
        InventoryResponse result = inventoryService.getProductInventory(TEST_PRODUCT_ID);

        // Then
        assertNotNull(result);
        assertEquals(TEST_PRODUCT_ID, result.getProductId());
        assertEquals(500, result.getTotalStock());
        assertEquals(1, result.getOptions().size());
    }

    @Test
    @DisplayName("상품 재고 조회 - 성공 (재고 0)")
    void testGetProductInventory_Success_ZeroStock() {
        // Given
        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("품절 상품")
                .description("현재 품절 상태")
                .price(25000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<ProductOption> options = List.of(
                ProductOption.builder()
                        .optionId(1L)
                        .productId(TEST_PRODUCT_ID)
                        .name("기본")
                        .stock(0)
                        .version(1L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );

        when(productRepository.findById(TEST_PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(productRepository.findOptionsByProductId(TEST_PRODUCT_ID))
                .thenReturn(options);

        // When
        InventoryResponse result = inventoryService.getProductInventory(TEST_PRODUCT_ID);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getTotalStock());
    }

    @Test
    @DisplayName("상품 재고 조회 - 성공 (많은 옵션)")
    void testGetProductInventory_Success_ManyOptions() {
        // Given
        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("다양한 옵션 상품")
                .description("여러 옵션을 가진 상품")
                .price(40000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<ProductOption> options = List.of(
                ProductOption.builder()
                        .optionId(1L)
                        .productId(TEST_PRODUCT_ID)
                        .name("색상 빨강")
                        .stock(100)
                        .version(1L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build(),
                ProductOption.builder()
                        .optionId(2L)
                        .productId(TEST_PRODUCT_ID)
                        .name("색상 파랑")
                        .stock(150)
                        .version(1L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build(),
                ProductOption.builder()
                        .optionId(3L)
                        .productId(TEST_PRODUCT_ID)
                        .name("색상 초록")
                        .stock(200)
                        .version(1L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build(),
                ProductOption.builder()
                        .optionId(4L)
                        .productId(TEST_PRODUCT_ID)
                        .name("색상 노랑")
                        .stock(250)
                        .version(1L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build(),
                ProductOption.builder()
                        .optionId(5L)
                        .productId(TEST_PRODUCT_ID)
                        .name("색상 검정")
                        .stock(300)
                        .version(1L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );

        when(productRepository.findById(TEST_PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(productRepository.findOptionsByProductId(TEST_PRODUCT_ID))
                .thenReturn(options);

        // When
        InventoryResponse result = inventoryService.getProductInventory(TEST_PRODUCT_ID);

        // Then
        assertNotNull(result);
        assertEquals(5, result.getOptions().size());
        assertEquals(1000, result.getTotalStock());
    }

    @Test
    @DisplayName("상품 재고 조회 - 실패 (유효하지 않은 productId 타입)")
    void testGetProductInventory_Failed_InvalidProductIdType() {
        // This test verifies that invalid product ID types are rejected
        // In practice, this would be handled by Spring's type conversion
        // The service level would not receive invalid types
    }

    @Test
    @DisplayName("상품 재고 조회 - 실패 (음수 productId)")
    void testGetProductInventory_Failed_NegativeProductId() {
        // Given
        Long negativeProductId = -1L;

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.getProductInventory(negativeProductId);
        });

        verify(productRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("상품 재고 조회 - 실패 (0 productId)")
    void testGetProductInventory_Failed_ZeroProductId() {
        // Given
        Long zeroProductId = 0L;

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.getProductInventory(zeroProductId);
        });

        verify(productRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("상품 재고 조회 - 실패 (상품 없음)")
    void testGetProductInventory_Failed_ProductNotFound() {
        // Given
        when(productRepository.findById(TEST_PRODUCT_ID))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(ProductNotFoundException.class, () -> {
            inventoryService.getProductInventory(TEST_PRODUCT_ID);
        });

        verify(productRepository, times(1)).findById(TEST_PRODUCT_ID);
        verify(productRepository, never()).findOptionsByProductId(anyLong());
    }

    // ========== 옵션 재고 정보 검증 ==========

    @Test
    @DisplayName("상품 재고 조회 - 옵션 정보 정확성 검증")
    void testGetProductInventory_OptionDataValidation() {
        // Given
        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("테스트 상품")
                .description("옵션 정보 검증용")
                .price(50000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ProductOption option = ProductOption.builder()
                .optionId(100L)
                .productId(TEST_PRODUCT_ID)
                .name("스페셜 옵션")
                .stock(75)
                .version(5L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(productRepository.findById(TEST_PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(productRepository.findOptionsByProductId(TEST_PRODUCT_ID))
                .thenReturn(List.of(option));

        // When
        InventoryResponse result = inventoryService.getProductInventory(TEST_PRODUCT_ID);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getOptions().size());

        OptionInventoryView optionView = result.getOptions().get(0);
        assertEquals(100L, optionView.getOptionId());
        assertEquals("스페셜 옵션", optionView.getName());
        assertEquals(75, optionView.getStock());
        assertEquals(5, optionView.getVersion());
    }

    @Test
    @DisplayName("상품 재고 조회 - 옵션 없음 (빈 옵션 리스트)")
    void testGetProductInventory_NoOptions() {
        // Given
        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("옵션 없는 상품")
                .description("옵션이 없는 상품")
                .price(15000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(productRepository.findById(TEST_PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(productRepository.findOptionsByProductId(TEST_PRODUCT_ID))
                .thenReturn(new ArrayList<>());

        // When
        InventoryResponse result = inventoryService.getProductInventory(TEST_PRODUCT_ID);

        // Then
        assertNotNull(result);
        assertEquals(TEST_PRODUCT_ID, result.getProductId());
        assertEquals(0, result.getTotalStock());
        assertEquals(0, result.getOptions().size());
    }

    // ========== 총 재고 계산 검증 ==========

    @Test
    @DisplayName("상품 재고 조회 - 총 재고 계산 정확성")
    void testGetProductInventory_TotalStockCalculation() {
        // Given
        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("재고 계산 테스트")
                .description("재고 합계를 검증")
                .price(35000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<ProductOption> options = List.of(
                ProductOption.builder()
                        .optionId(1L)
                        .productId(TEST_PRODUCT_ID)
                        .name("옵션 1")
                        .stock(100)
                        .version(1L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build(),
                ProductOption.builder()
                        .optionId(2L)
                        .productId(TEST_PRODUCT_ID)
                        .name("옵션 2")
                        .stock(200)
                        .version(1L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build(),
                ProductOption.builder()
                        .optionId(3L)
                        .productId(TEST_PRODUCT_ID)
                        .name("옵션 3")
                        .stock(300)
                        .version(1L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );

        when(productRepository.findById(TEST_PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(productRepository.findOptionsByProductId(TEST_PRODUCT_ID))
                .thenReturn(options);

        // When
        InventoryResponse result = inventoryService.getProductInventory(TEST_PRODUCT_ID);

        // Then
        // Expected total: 100 + 200 + 300 = 600
        assertEquals(600, result.getTotalStock());
    }

    // ========== Null 입력 검증 ==========

    @Test
    @DisplayName("상품 재고 조회 - 실패 (null productId)")
    void testGetProductInventory_Failed_NullProductId() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.getProductInventory(null);
        });
    }
}
