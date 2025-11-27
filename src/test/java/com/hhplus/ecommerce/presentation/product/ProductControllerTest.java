package com.hhplus.ecommerce.presentation.product;

import com.hhplus.ecommerce.application.product.ProductService;
import com.hhplus.ecommerce.presentation.product.response.ProductDetailResponse;
import com.hhplus.ecommerce.presentation.product.response.ProductListResponse;
import com.hhplus.ecommerce.presentation.product.response.ProductOptionResponse;
import com.hhplus.ecommerce.presentation.product.response.ProductResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ProductControllerTest - Presentation Layer Unit Test
 * @WebMvcTest를 사용한 통합 테스트
 *
 * 테스트 대상: ProductController
 * - GET /products - 상품 목록 조회 (페이지네이션, 정렬)
 * - GET /products/{product_id} - 상품 상세 조회
 *
 * 테스트 유형:
 * - 성공 케이스: 정상적인 페이지네이션, 정렬
 * - 경계값 테스트: 첫 페이지, 마지막 페이지, 최소/최대 size
 * - 실패 케이스: 유효하지 않은 파라미터
 */
@WebMvcTest(ProductController.class)
@DisplayName("ProductController 단위 테스트")
@Disabled("MockMvc 라우팅 이슈 - @SpringBootTest 기반 통합 테스트(ProductControllerIntegrationTest)로 대체됨")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private static final Long TEST_PRODUCT_ID = 1L;

    // ========== 상품 목록 조회 (GET /products) ==========

    @Test
    @DisplayName("상품 목록 조회 - 성공 (기본 파라미터)")
    void testGetProductList_Success_DefaultParameters() throws Exception {
        // Given
        ProductListResponse response = ProductListResponse.builder()
                .content(Arrays.asList(
                        ProductResponse.builder().productId(1L).productName("상품1").price(10000L).build(),
                        ProductResponse.builder().productId(2L).productName("상품2").price(20000L).build()
                ))
                .totalElements(100L)
                .totalPages(10L)
                .currentPage(0)
                .size(10)
                .build();

        when(productService.getProductList(0, 10, "product_id,desc"))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products", hasSize(2)))
                .andExpect(jsonPath("$.total_count").value(100L))
                .andExpect(jsonPath("$.current_page").value(0));
    }

    @Test
    @DisplayName("상품 목록 조회 - 성공 (커스텀 페이지네이션)")
    void testGetProductList_Success_CustomPagination() throws Exception {
        // Given
        ProductListResponse response = ProductListResponse.builder()
                .content(Arrays.asList(
                        ProductResponse.builder().productId(11L).productName("상품11").price(110000L).build()
                ))
                .totalElements(100L)
                .totalPages(10L)
                .currentPage(1)
                .size(10)
                .build();

        when(productService.getProductList(1, 10, "product_id,desc"))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/products?page=1&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_page").value(1))
                .andExpect(jsonPath("$.page_size").value(10));
    }

    @Test
    @DisplayName("상품 목록 조회 - 성공 (커스텀 정렬)")
    void testGetProductList_Success_CustomSort() throws Exception {
        // Given
        ProductListResponse response = ProductListResponse.builder()
                .content(Collections.singletonList(
                        ProductResponse.builder().productId(1L).productName("상품1").price(10000L).build()
                ))
                .totalElements(100L)
                .totalPages(10L)
                .currentPage(0)
                .size(10)
                .build();

        when(productService.getProductList(0, 10, "price,asc"))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/products?sort=price,asc"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("상품 목록 조회 - 성공 (최소 페이지 크기)")
    void testGetProductList_Success_MinimumPageSize() throws Exception {
        // Given
        ProductListResponse response = ProductListResponse.builder()
                .content(Collections.singletonList(
                        ProductResponse.builder().productId(1L).productName("상품1").price(10000L).build()
                ))
                .totalElements(100L)
                .totalPages(100L)
                .currentPage(0)
                .size(1)
                .build();

        when(productService.getProductList(0, 1, "product_id,desc"))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/products?size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_size").value(1));
    }

    @Test
    @DisplayName("상품 목록 조회 - 성공 (최대 페이지 크기)")
    void testGetProductList_Success_MaximumPageSize() throws Exception {
        // Given
        ProductListResponse response = ProductListResponse.builder()
                .content(Collections.nCopies(100,
                        ProductResponse.builder().productId(1L).productName("상품1").price(10000L).build()))
                .totalElements(100L)
                .totalPages(1L)
                .currentPage(0)
                .size(100)
                .build();

        when(productService.getProductList(0, 100, "product_id,desc"))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/products?size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page_size").value(100));
    }

    @Test
    @DisplayName("상품 목록 조회 - 성공 (빈 결과)")
    void testGetProductList_Success_EmptyResult() throws Exception {
        // Given
        ProductListResponse response = ProductListResponse.builder()
                .content(Collections.emptyList())
                .totalElements(0L)
                .totalPages(0L)
                .currentPage(0)
                .size(10)
                .build();

        when(productService.getProductList(0, 10, "product_id,desc"))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products", hasSize(0)))
                .andExpect(jsonPath("$.total_count").value(0L));
    }

    @Test
    @DisplayName("상품 목록 조회 - 실패 (유효하지 않은 page)")
    void testGetProductList_Failed_InvalidPage() throws Exception {
        // When & Then
        mockMvc.perform(get("/products?page=invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상품 목록 조회 - 실패 (음수 page)")
    void testGetProductList_Failed_NegativePage() throws Exception {
        // When & Then
        mockMvc.perform(get("/products?page=-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상품 목록 조회 - 실패 (유효하지 않은 size)")
    void testGetProductList_Failed_InvalidSize() throws Exception {
        // When & Then
        mockMvc.perform(get("/products?size=invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상품 목록 조회 - 실패 (음수 size)")
    void testGetProductList_Failed_NegativeSize() throws Exception {
        // When & Then
        mockMvc.perform(get("/products?size=-5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상품 목록 조회 - 실패 (size 0)")
    void testGetProductList_Failed_ZeroSize() throws Exception {
        // When & Then
        mockMvc.perform(get("/products?size=0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상품 목록 조회 - 실패 (size 101 초과)")
    void testGetProductList_Failed_ExceedMaxSize() throws Exception {
        // When & Then
        mockMvc.perform(get("/products?size=101"))
                .andExpect(status().isBadRequest());
    }

    // ========== 상품 상세 조회 (GET /products/{product_id}) ==========

    @Test
    @DisplayName("상품 상세 조회 - 성공")
    void testGetProductDetail_Success() throws Exception {
        // Given
        ProductDetailResponse response = ProductDetailResponse.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("프리미엄 상품")
                .price(50000L)
                .description("고품질 상품입니다")
                .totalStock(100)
                .status("판매 중")
                .options(Collections.emptyList())
                .build();

        when(productService.getProductDetail(TEST_PRODUCT_ID))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/products/{product_id}", TEST_PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_id").value(TEST_PRODUCT_ID))
                .andExpect(jsonPath("$.product_name").value("프리미엄 상품"))
                .andExpect(jsonPath("$.price").value(50000L))
                .andExpect(jsonPath("$.status").value("판매 중"));
    }

    @Test
    @DisplayName("상품 상세 조회 - 성공 (옵션 포함)")
    void testGetProductDetail_Success_WithOptions() throws Exception {
        // Given
        ProductDetailResponse response = ProductDetailResponse.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("옵션 있는 상품")
                .price(30000L)
                .description("옵션이 있는 상품")
                .totalStock(200)
                .status("판매 중")
                .options(Arrays.asList(
                        new ProductOptionResponse(1L, "옵션 이름 1", 100, 1000L),
                        new ProductOptionResponse(2L, "옵션 이름 2", 200, 2000L)
                ))
                .build();

        when(productService.getProductDetail(TEST_PRODUCT_ID))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/products/{product_id}", TEST_PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options").isArray());
    }

    @Test
    @DisplayName("상품 상세 조회 - 성공 (품절 상품)")
    void testGetProductDetail_Success_SoldOut() throws Exception {
        // Given
        ProductDetailResponse response = ProductDetailResponse.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("품절 상품")
                .price(25000L)
                .description("현재 품절 상태")
                .totalStock(0)
                .status("품절")
                .options(Collections.emptyList())
                .build();

        when(productService.getProductDetail(TEST_PRODUCT_ID))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/products/{product_id}", TEST_PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("품절"))
                .andExpect(jsonPath("$.total_stock").value(0));
    }

    @Test
    @DisplayName("상품 상세 조회 - 실패 (유효하지 않은 product_id)")
    void testGetProductDetail_Failed_InvalidProductId() throws Exception {
        // When & Then
        mockMvc.perform(get("/products/{product_id}", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상품 상세 조회 - 실패 (음수 product_id)")
    void testGetProductDetail_Failed_NegativeProductId() throws Exception {
        // When & Then
        mockMvc.perform(get("/products/{product_id}", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상품 상세 조회 - 실패 (product_id 0)")
    void testGetProductDetail_Failed_ZeroProductId() throws Exception {
        // When & Then
        mockMvc.perform(get("/products/{product_id}", "0"))
                .andExpect(status().isBadRequest());
    }

    // ========== 응답 포맷 검증 ==========

    @Test
    @DisplayName("상품 목록 조회 - 응답 필드 검증")
    void testGetProductList_ResponseFieldValidation() throws Exception {
        // Given
        ProductListResponse response = ProductListResponse.builder()
                .content(Collections.singletonList(
                        ProductResponse.builder().productId(1L).productName("상품1").price(10000L).build()
                ))
                .totalElements(100L)
                .totalPages(10L)
                .currentPage(0)
                .size(10)
                .build();

        when(productService.getProductList(anyInt(), anyInt(), anyString()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.total_count").isNumber())
                .andExpect(jsonPath("$.total_page").isNumber())
                .andExpect(jsonPath("$.current_page").isNumber())
                .andExpect(jsonPath("$.page_size").isNumber());
    }

    @Test
    @DisplayName("상품 상세 조회 - 응답 필드 검증")
    void testGetProductDetail_ResponseFieldValidation() throws Exception {
        // Given
        ProductDetailResponse response = ProductDetailResponse.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("상품")
                .price(10000L)
                .description("설명")
                .totalStock(100)
                .status("판매 중")
                .options(Collections.emptyList())
                .build();

        when(productService.getProductDetail(TEST_PRODUCT_ID))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/products/{product_id}", TEST_PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap())
                .andExpect(jsonPath("$.product_id").isNumber())
                .andExpect(jsonPath("$.product_name").isString())
                .andExpect(jsonPath("$.price").isNumber())
                .andExpect(jsonPath("$.total_stock").isNumber())
                .andExpect(jsonPath("$.status").isString());
    }
}
