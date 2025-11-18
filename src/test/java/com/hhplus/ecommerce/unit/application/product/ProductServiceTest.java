package com.hhplus.ecommerce.unit.application.product;


import com.hhplus.ecommerce.application.product.ProductService;import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductNotFoundException;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.presentation.product.response.ProductDetailResponse;
import com.hhplus.ecommerce.presentation.product.response.ProductListResponse;
import com.hhplus.ecommerce.presentation.product.response.ProductResponse;
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
 * ProductServiceTest - Application 계층 단위 테스트
 * Spring Boot 3.4+ Mockito 방식 테스트
 *
 * 테스트 대상: ProductService
 * - 상품 목록 조회 (페이지네이션, 정렬)
 * - 상품 상세 조회 (옵션 포함)
 * - 인기 상품 조회
 *
 * 테스트 유형:
 * - 성공 케이스: 정상적인 상품 조회, 페이지네이션, 정렬
 * - 경계값 테스트: 페이지 범위, 페이지 크기, 정렬
 * - 예외 케이스: 유효하지 않은 파라미터, 상품 없음
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService 단위 테스트")
class ProductServiceTest {

    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    private static final Long TEST_PRODUCT_ID = 1L;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        productService = new ProductService(productRepository);
    }

    // ========== 상품 목록 조회 (getProductList) ==========

    @Test
    @DisplayName("상품 목록 조회 - 성공 (첫 페이지, 기본 크기)")
    void testGetProductList_Success_FirstPage() {
        // Given
        List<Product> allProducts = createProductList(25);
        when(productRepository.findAll()).thenReturn(allProducts);

        // When
        ProductListResponse result = productService.getProductList(0, 10, "product_id,asc");

        // Then
        assertNotNull(result);
        assertEquals(10, result.getContent().size());
        assertEquals(25L, result.getTotalElements());
        assertEquals(3L, result.getTotalPages());
        assertEquals(0, result.getCurrentPage());
        assertEquals(10, result.getSize());

        verify(productRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("상품 목록 조회 - 성공 (두 번째 페이지)")
    void testGetProductList_Success_SecondPage() {
        // Given
        List<Product> allProducts = createProductList(25);
        when(productRepository.findAll()).thenReturn(allProducts);

        // When
        ProductListResponse result = productService.getProductList(1, 10, "product_id,asc");

        // Then
        assertNotNull(result);
        assertEquals(10, result.getContent().size());
        assertEquals(25L, result.getTotalElements());
        assertEquals(1, result.getCurrentPage());

        verify(productRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("상품 목록 조회 - 성공 (마지막 페이지, 부분 항목)")
    void testGetProductList_Success_LastPage() {
        // Given
        List<Product> allProducts = createProductList(25);
        when(productRepository.findAll()).thenReturn(allProducts);

        // When
        ProductListResponse result = productService.getProductList(2, 10, "product_id,asc");

        // Then
        assertNotNull(result);
        assertEquals(5, result.getContent().size());
        assertEquals(25L, result.getTotalElements());
        assertEquals(2, result.getCurrentPage());

        verify(productRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("상품 목록 조회 - 성공 (페이지 범위 벗어남)")
    void testGetProductList_Success_PageOutOfRange() {
        // Given
        List<Product> allProducts = createProductList(20);
        when(productRepository.findAll()).thenReturn(allProducts);

        // When
        ProductListResponse result = productService.getProductList(5, 10, "product_id,asc");

        // Then
        assertNotNull(result);
        assertEquals(0, result.getContent().size());
        assertEquals(20L, result.getTotalElements());
        assertEquals(5, result.getCurrentPage());

        verify(productRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("상품 목록 조회 - 실패 (음수 페이지)")
    void testGetProductList_Failed_NegativePage() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            productService.getProductList(-1, 10, "product_id,asc");
        });
    }

    @Test
    @DisplayName("상품 목록 조회 - 실패 (0 페이지 크기)")
    void testGetProductList_Failed_ZeroPageSize() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            productService.getProductList(0, 0, "product_id,asc");
        });
    }

    @Test
    @DisplayName("상품 목록 조회 - 실패 (페이지 크기 초과)")
    void testGetProductList_Failed_ExceededPageSize() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            productService.getProductList(0, 101, "product_id,asc");
        });
    }

    @Test
    @DisplayName("상품 목록 조회 - 성공 (정렬 - 가격 오름차순)")
    void testGetProductList_Success_SortByPriceAsc() {
        // Given
        List<Product> allProducts = List.of(
                createProduct(1L, "상품1", 50000L),
                createProduct(2L, "상품2", 30000L),
                createProduct(3L, "상품3", 80000L)
        );
        when(productRepository.findAll()).thenReturn(allProducts);

        // When
        ProductListResponse result = productService.getProductList(0, 10, "price,asc");

        // Then
        assertNotNull(result);
        assertEquals(3, result.getContent().size());
        // First product should have lowest price
        assertEquals(30000L, result.getContent().get(0).getPrice());
    }

    @Test
    @DisplayName("상품 목록 조회 - 성공 (정렬 - 가격 내림차순)")
    void testGetProductList_Success_SortByPriceDesc() {
        // Given
        List<Product> allProducts = List.of(
                createProduct(1L, "상품1", 50000L),
                createProduct(2L, "상품2", 30000L),
                createProduct(3L, "상품3", 80000L)
        );
        when(productRepository.findAll()).thenReturn(allProducts);

        // When
        ProductListResponse result = productService.getProductList(0, 10, "price,desc");

        // Then
        assertNotNull(result);
        assertEquals(3, result.getContent().size());
        // First product should have highest price
        assertEquals(80000L, result.getContent().get(0).getPrice());
    }

    @Test
    @DisplayName("상품 목록 조회 - 성공 (빈 결과)")
    void testGetProductList_Success_EmptyResult() {
        // Given
        when(productRepository.findAll()).thenReturn(new ArrayList<>());

        // When
        ProductListResponse result = productService.getProductList(0, 10, "product_id,asc");

        // Then
        assertNotNull(result);
        assertEquals(0, result.getContent().size());
        assertEquals(0L, result.getTotalElements());
        assertEquals(0L, result.getTotalPages());
    }

    // ========== 상품 상세 조회 (getProductDetail) ==========

    @Test
    @DisplayName("상품 상세 조회 - 성공 (옵션 포함)")
    void testGetProductDetail_Success_WithOptions() {
        // Given
        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("프리미엄 상품")
                .description("상품 설명")
                .price(100000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<ProductOption> options = List.of(
                createProductOption(1L, TEST_PRODUCT_ID, "옵션1", 50000L),
                createProductOption(2L, TEST_PRODUCT_ID, "옵션2", 60000L)
        );

        when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepository.findOptionsByProductId(TEST_PRODUCT_ID)).thenReturn(options);

        // When
        ProductDetailResponse result = productService.getProductDetail(TEST_PRODUCT_ID);

        // Then
        assertNotNull(result);
        assertEquals(TEST_PRODUCT_ID, result.getProductId());
        assertEquals("프리미엄 상품", result.getProductName());
        assertEquals(100000L, result.getPrice());
        assertEquals(2, result.getOptions().size());

        verify(productRepository, times(1)).findById(TEST_PRODUCT_ID);
        verify(productRepository, times(1)).findOptionsByProductId(TEST_PRODUCT_ID);
    }

    @Test
    @DisplayName("상품 상세 조회 - 성공 (옵션 없음)")
    void testGetProductDetail_Success_NoOptions() {
        // Given
        Product product = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("기본 상품")
                .description("옵션 없는 상품")
                .price(50000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepository.findOptionsByProductId(TEST_PRODUCT_ID)).thenReturn(new ArrayList<>());

        // When
        ProductDetailResponse result = productService.getProductDetail(TEST_PRODUCT_ID);

        // Then
        assertNotNull(result);
        assertEquals(TEST_PRODUCT_ID, result.getProductId());
        assertEquals(0, result.getOptions().size());
    }

    @Test
    @DisplayName("상품 상세 조회 - 실패 (상품 없음)")
    void testGetProductDetail_Failed_ProductNotFound() {
        // Given
        when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ProductNotFoundException.class, () -> {
            productService.getProductDetail(TEST_PRODUCT_ID);
        });

        verify(productRepository, times(1)).findById(TEST_PRODUCT_ID);
        verify(productRepository, never()).findOptionsByProductId(anyLong());
    }

    @Test
    @DisplayName("상품 상세 조회 - 실패 (음수 productId)")
    void testGetProductDetail_Failed_NegativeProductId() {
        // Given
        Long negativeProductId = -1L;

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            productService.getProductDetail(negativeProductId);
        });

        verify(productRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("상품 상세 조회 - 실패 (0 productId)")
    void testGetProductDetail_Failed_ZeroProductId() {
        // Given
        Long zeroProductId = 0L;

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            productService.getProductDetail(zeroProductId);
        });

        verify(productRepository, never()).findById(anyLong());
    }

    // ========== 페이지네이션 경계값 테스트 ==========

    @Test
    @DisplayName("상품 목록 조회 - 페이지네이션 계산 정확성 (정확한 분할)")
    void testGetProductList_PaginationCalculation_ExactDivision() {
        // Given: 30개 상품, 페이지 크기 10 = 3 페이지
        List<Product> allProducts = createProductList(30);
        when(productRepository.findAll()).thenReturn(allProducts);

        // When
        ProductListResponse result = productService.getProductList(0, 10, "product_id,asc");

        // Then
        assertEquals(3L, result.getTotalPages());
        assertEquals(30L, result.getTotalElements());
    }

    @Test
    @DisplayName("상품 목록 조회 - 페이지네이션 계산 정확성 (불완전한 분할)")
    void testGetProductList_PaginationCalculation_IncompleteDivision() {
        // Given: 35개 상품, 페이지 크기 10 = 4 페이지
        List<Product> allProducts = createProductList(35);
        when(productRepository.findAll()).thenReturn(allProducts);

        // When
        ProductListResponse result = productService.getProductList(0, 10, "product_id,asc");

        // Then
        assertEquals(4L, result.getTotalPages());
        assertEquals(35L, result.getTotalElements());
    }

    @Test
    @DisplayName("상품 목록 조회 - 페이지네이션 계산 정확성 (단일 항목)")
    void testGetProductList_PaginationCalculation_SingleItem() {
        // Given: 1개 상품, 페이지 크기 10 = 1 페이지
        List<Product> allProducts = createProductList(1);
        when(productRepository.findAll()).thenReturn(allProducts);

        // When
        ProductListResponse result = productService.getProductList(0, 10, "product_id,asc");

        // Then
        assertEquals(1L, result.getTotalPages());
        assertEquals(1L, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    // ========== 페이지 크기 다양성 테스트 ==========

    @Test
    @DisplayName("상품 목록 조회 - 다양한 페이지 크기 (20)")
    void testGetProductList_DifferentPageSize_20() {
        // Given
        List<Product> allProducts = createProductList(50);
        when(productRepository.findAll()).thenReturn(allProducts);

        // When
        ProductListResponse result = productService.getProductList(0, 20, "product_id,asc");

        // Then
        assertEquals(20, result.getContent().size());
        assertEquals(3L, result.getTotalPages());
    }

    @Test
    @DisplayName("상품 목록 조회 - 다양한 페이지 크기 (최대 100)")
    void testGetProductList_MaxPageSize() {
        // Given
        List<Product> allProducts = createProductList(150);
        when(productRepository.findAll()).thenReturn(allProducts);

        // When
        ProductListResponse result = productService.getProductList(0, 100, "product_id,asc");

        // Then
        assertEquals(100, result.getContent().size());
        assertEquals(2L, result.getTotalPages());
    }

    // ========== Helper 메서드 ==========

    private List<Product> createProductList(int size) {
        List<Product> products = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            products.add(createProduct((long) i, "상품" + i, (long) (10000 * i)));
        }
        return products;
    }

    private Product createProduct(Long id, String name, Long price) {
        return Product.builder()
                .productId(id)
                .productName(name)
                .description("설명 " + name)
                .price(price)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private ProductOption createProductOption(Long optionId, Long productId, String name, Long price) {
        return ProductOption.builder()
                .optionId(optionId)
                .productId(productId)
                .name(name)
                .stock(100)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
