package com.hhplus.ecommerce.infrastructure.persistence.product;

import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InMemoryProductRepository 단위 테스트
 * - Product 조회 및 저장
 * - ProductOption 관리
 * - 최근 3일 주문 수량 조회
 */
@DisplayName("InMemoryProductRepository 테스트")
class InMemoryProductRepositoryTest {

    private InMemoryProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
    }

    // ========== Product 조회 ==========

    @Test
    @DisplayName("findAll - 모든 상품 조회")
    void testFindAll_GetAllProducts() {
        // When
        List<Product> products = productRepository.findAll();

        // Then
        assertNotNull(products);
        assertFalse(products.isEmpty());
        assertTrue(products.size() >= 10);
    }

    @Test
    @DisplayName("findById - 기존 상품 조회")
    void testFindById_ExistingProduct() {
        // When
        Optional<Product> product = productRepository.findById(1L);

        // Then
        assertTrue(product.isPresent());
        assertEquals(1L, product.get().getProductId());
        assertEquals("티셔츠", product.get().getProductName());
        assertEquals(29900L, product.get().getPrice());
    }

    @Test
    @DisplayName("findById - 없는 상품은 Optional.empty() 반환")
    void testFindById_NonExistingProduct() {
        // When
        Optional<Product> product = productRepository.findById(999L);

        // Then
        assertTrue(product.isEmpty());
    }

    @Test
    @DisplayName("findById - 품절 상품 조회")
    void testFindById_SoldOutProduct() {
        // When
        Optional<Product> product = productRepository.findById(7L);

        // Then
        assertTrue(product.isPresent());
        assertEquals("품절", product.get().getStatus());
        assertEquals(0, product.get().getTotalStock());
    }

    // ========== ProductOption 조회 ==========

    @Test
    @DisplayName("findOptionsByProductId - 상품의 모든 옵션 조회")
    void testFindOptionsByProductId_GetAllOptions() {
        // When
        List<ProductOption> options = productRepository.findOptionsByProductId(1L);

        // Then
        assertNotNull(options);
        assertFalse(options.isEmpty());
        assertEquals(3, options.size());
    }

    @Test
    @DisplayName("findOptionsByProductId - 옵션이 없는 상품")
    void testFindOptionsByProductId_NoOptions() {
        // When
        List<ProductOption> options = productRepository.findOptionsByProductId(4L);

        // Then
        assertNotNull(options);
        assertTrue(options.isEmpty());
    }

    @Test
    @DisplayName("findOptionsByProductId - 없는 상품ID는 빈 리스트")
    void testFindOptionsByProductId_NonExistingProduct() {
        // When
        List<ProductOption> options = productRepository.findOptionsByProductId(999L);

        // Then
        assertNotNull(options);
        assertTrue(options.isEmpty());
    }

    @Test
    @DisplayName("findOptionById - 기존 옵션 조회")
    void testFindOptionById_ExistingOption() {
        // When
        Optional<ProductOption> option = productRepository.findOptionById(101L);

        // Then
        assertTrue(option.isPresent());
        assertEquals(101L, option.get().getOptionId());
        assertEquals(1L, option.get().getProductId());
        assertEquals("블랙/M", option.get().getName());
    }

    @Test
    @DisplayName("findOptionById - 없는 옵션은 Optional.empty() 반환")
    void testFindOptionById_NonExistingOption() {
        // When
        Optional<ProductOption> option = productRepository.findOptionById(999L);

        // Then
        assertTrue(option.isEmpty());
    }

    // ========== 최근 3일 주문 수량 ==========

    @Test
    @DisplayName("getOrderCount3Days - 상품의 최근 3일 주문 수량 조회")
    void testGetOrderCount3Days_GetOrderCount() {
        // When
        Long count = productRepository.getOrderCount3Days(1L);

        // Then
        assertNotNull(count);
        assertEquals(150L, count);
    }

    @Test
    @DisplayName("getOrderCount3Days - 없는 상품ID는 0 반환")
    void testGetOrderCount3Days_NonExistingProduct() {
        // When
        Long count = productRepository.getOrderCount3Days(999L);

        // Then
        assertEquals(0L, count);
    }

    @Test
    @DisplayName("getOrderCount3Days - 모든 상품의 주문 수량 확인")
    void testGetOrderCount3Days_AllProducts() {
        // When/Then
        assertEquals(150L, productRepository.getOrderCount3Days(1L));
        assertEquals(120L, productRepository.getOrderCount3Days(2L));
        assertEquals(180L, productRepository.getOrderCount3Days(3L));
        assertEquals(95L, productRepository.getOrderCount3Days(4L));
        assertEquals(110L, productRepository.getOrderCount3Days(5L));
    }

    // ========== Product 저장 ==========

    @Test
    @DisplayName("save - 새 상품 저장")
    void testSave_SaveNewProduct() {
        // When
        Product newProduct = Product.builder()
                .productId(100L)
                .productName("새 상품")
                .description("새로운 상품입니다")
                .price(49900L)
                .totalStock(50)
                .status("판매 중")
                .build();

        productRepository.save(newProduct);

        // Then
        Optional<Product> saved = productRepository.findById(100L);
        assertTrue(saved.isPresent());
        assertEquals("새 상품", saved.get().getProductName());
    }

    @Test
    @DisplayName("save - 기존 상품 업데이트")
    void testSave_UpdateExistingProduct() {
        // Given
        Optional<Product> existing = productRepository.findById(1L);
        assertTrue(existing.isPresent());
        Product product = existing.get();

        // When - Builder로 변경된 객체 생성
        Product updatedProduct = Product.builder()
                .productId(product.getProductId())
                .productName("변경된 상품명")
                .description(product.getDescription())
                .price(39900L)
                .totalStock(product.getTotalStock())
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .options(product.getOptions())
                .build();
        productRepository.save(updatedProduct);

        // Then
        Optional<Product> updated = productRepository.findById(1L);
        assertTrue(updated.isPresent());
        assertEquals("변경된 상품명", updated.get().getProductName());
        assertEquals(39900L, updated.get().getPrice());
    }

    // ========== ProductOption 저장 ==========

    @Test
    @DisplayName("saveOption - 새 옵션 저장")
    void testSaveOption_SaveNewOption() {
        // When
        ProductOption newOption = ProductOption.builder()
                .optionId(999L)
                .productId(1L)
                .name("새로운 옵션")
                .stock(50)
                .version(1L)
                .build();

        productRepository.saveOption(newOption);

        // Then
        Optional<ProductOption> saved = productRepository.findOptionById(999L);
        assertTrue(saved.isPresent());
        assertEquals("새로운 옵션", saved.get().getName());
    }

    @Test
    @DisplayName("saveOption - 기존 옵션 업데이트")
    void testSaveOption_UpdateExistingOption() {
        // Given
        Optional<ProductOption> existing = productRepository.findOptionById(101L);
        assertTrue(existing.isPresent());
        ProductOption option = existing.get();

        // When - Builder로 변경된 객체 생성
        ProductOption updatedOption = ProductOption.builder()
                .optionId(option.getOptionId())
                .productId(option.getProductId())
                .name(option.getName())
                .stock(100)
                .version(2L)
                .createdAt(option.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.saveOption(updatedOption);

        // Then
        Optional<ProductOption> updated = productRepository.findOptionById(101L);
        assertTrue(updated.isPresent());
        assertEquals(100, updated.get().getStock());
        assertEquals(2L, updated.get().getVersion());
    }

    // ========== 데이터 초기화 검증 ==========

    @Test
    @DisplayName("초기화 데이터 - 기본 상품 데이터 확인")
    void testInitialData_SampleProducts() {
        // Then
        assertEquals("티셔츠", productRepository.findById(1L).get().getProductName());
        assertEquals("청바지", productRepository.findById(2L).get().getProductName());
        assertEquals("슬리퍼", productRepository.findById(3L).get().getProductName());
        assertEquals("후드 집업", productRepository.findById(4L).get().getProductName());
        assertEquals("치마", productRepository.findById(5L).get().getProductName());
    }

    @Test
    @DisplayName("초기화 데이터 - 상품 옵션 데이터 확인")
    void testInitialData_SampleOptions() {
        // Then
        List<ProductOption> options1 = productRepository.findOptionsByProductId(1L);
        assertEquals(3, options1.size());

        List<ProductOption> options2 = productRepository.findOptionsByProductId(2L);
        assertEquals(2, options2.size());
    }

    @Test
    @DisplayName("초기화 데이터 - 상품 상태 다양성")
    void testInitialData_VariousStatuses() {
        // Then
        assertEquals("판매 중", productRepository.findById(1L).get().getStatus());
        assertEquals("품절", productRepository.findById(7L).get().getStatus());
        assertEquals("판매 중지", productRepository.findById(9L).get().getStatus());
    }

    // ========== 실제 사용 시나리오 ==========

    @Test
    @DisplayName("사용 시나리오 - 상품 목록 페이징 조회 시뮬레이션")
    void testScenario_ProductListPagination() {
        // When
        List<Product> allProducts = productRepository.findAll();

        // Then
        int pageSize = 5;
        int totalPages = (allProducts.size() + pageSize - 1) / pageSize;
        assertTrue(totalPages >= 2);
    }

    @Test
    @DisplayName("사용 시나리오 - 상품 상세 조회 및 옵션 로드")
    void testScenario_ProductDetailWithOptions() {
        // When
        Optional<Product> product = productRepository.findById(1L);
        assertTrue(product.isPresent());

        List<ProductOption> options = productRepository.findOptionsByProductId(1L);

        // Then
        assertEquals("티셔츠", product.get().getProductName());
        assertFalse(options.isEmpty());
        assertTrue(options.stream().anyMatch(opt -> "블랙/M".equals(opt.getName())));
    }

    @Test
    @DisplayName("사용 시나리오 - 인기상품 순위 조회")
    void testScenario_PopularProductRanking() {
        // When
        List<Product> allProducts = productRepository.findAll();

        // Then
        // 최근 3일 주문수량으로 정렬
        allProducts.stream()
                .filter(p -> productRepository.getOrderCount3Days(p.getProductId()) > 0)
                .sorted((p1, p2) -> Long.compare(
                        productRepository.getOrderCount3Days(p2.getProductId()),
                        productRepository.getOrderCount3Days(p1.getProductId())
                ))
                .limit(3)
                .forEach(p -> {
                    Long count = productRepository.getOrderCount3Days(p.getProductId());
                    assertNotNull(count);
                    assertTrue(count > 0);
                });
    }

    @Test
    @DisplayName("사용 시나리오 - 옵션별 재고 확인")
    void testScenario_CheckOptionStock() {
        // When
        List<ProductOption> options = productRepository.findOptionsByProductId(1L);

        // Then
        options.forEach(option -> {
            assertNotNull(option.getStock());
            assertTrue(option.getStock() >= 0);
        });
    }
}
