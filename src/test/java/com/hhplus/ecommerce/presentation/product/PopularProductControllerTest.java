package com.hhplus.ecommerce.presentation.product;

import com.hhplus.ecommerce.application.product.PopularProductService;
import com.hhplus.ecommerce.common.BaseControllerTest;
import com.hhplus.ecommerce.presentation.product.response.PopularProductListResponse;
import com.hhplus.ecommerce.presentation.product.response.PopularProductView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PopularProductControllerTest - Presentation Layer Unit Test
 * Mockito 및 MockMvcBuilders.standaloneSetup() 사용
 *
 * 테스트 대상: PopularProductController
 * - GET /products/popular - 인기 상품 목록 조회 (상위 5개, 최근 3일 판매량 기준)
 *
 * 테스트 유형:
 * - 성공 케이스: 정상적인 인기 상품 조회 (5개, 3개, 1개)
 * - 경계값 테스트: 빈 결과
 * - 응답 필드 검증: rank 정보, 정렬 순서 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PopularProductController 단위 테스트")
class PopularProductControllerTest extends BaseControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private PopularProductService popularProductService;

    @InjectMocks
    private PopularProductController popularProductController;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        this.mockMvc = MockMvcBuilders.standaloneSetup(popularProductController).build();
        this.objectMapper = new ObjectMapper();
    }

    // ========== 인기 상품 조회 (GET /products/popular) ==========

    @Test
    @DisplayName("인기 상품 조회 - 성공 (5개 상품)")
    void testGetPopularProducts_Success_FiveProducts() throws Exception {
        // Given
        PopularProductListResponse response = PopularProductListResponse.builder()
                .products(Arrays.asList(
                        PopularProductView.builder()
                                .productId(1L)
                                .productName("최고 인기 상품")
                                .price(100000L)
                                .totalStock(500)
                                .status("판매 중")
                                .orderCount3Days(1500L)
                                .rank(1)
                                .createdAt(LocalDateTime.now().minusDays(10))
                                .build(),
                        PopularProductView.builder()
                                .productId(2L)
                                .productName("2위 인기 상품")
                                .price(80000L)
                                .totalStock(450)
                                .status("판매 중")
                                .orderCount3Days(1200L)
                                .rank(2)
                                .createdAt(LocalDateTime.now().minusDays(8))
                                .build(),
                        PopularProductView.builder()
                                .productId(3L)
                                .productName("3위 인기 상품")
                                .price(70000L)
                                .totalStock(400)
                                .status("판매 중")
                                .orderCount3Days(1000L)
                                .rank(3)
                                .createdAt(LocalDateTime.now().minusDays(6))
                                .build(),
                        PopularProductView.builder()
                                .productId(4L)
                                .productName("4위 인기 상품")
                                .price(60000L)
                                .totalStock(350)
                                .status("판매 중")
                                .orderCount3Days(800L)
                                .rank(4)
                                .createdAt(LocalDateTime.now().minusDays(4))
                                .build(),
                        PopularProductView.builder()
                                .productId(5L)
                                .productName("5위 인기 상품")
                                .price(50000L)
                                .totalStock(300)
                                .status("판매 중")
                                .orderCount3Days(600L)
                                .rank(5)
                                .createdAt(LocalDateTime.now().minusDays(2))
                                .build()
                ))
                .build();

        when(popularProductService.getPopularProducts())
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products", hasSize(5)))
                .andExpect(jsonPath("$.products[0].product_name").value("최고 인기 상품"))
                .andExpect(jsonPath("$.products[0].rank").value(1))
                .andExpect(jsonPath("$.products[1].rank").value(2))
                .andExpect(jsonPath("$.products[4].rank").value(5))
                .andExpect(jsonPath("$.products[0].order_count_3days").value(1500L));

        verify(popularProductService, times(1)).getPopularProducts();
    }

    @Test
    @DisplayName("인기 상품 조회 - 성공 (3개 상품)")
    void testGetPopularProducts_Success_ThreeProducts() throws Exception {
        // Given
        PopularProductListResponse response = PopularProductListResponse.builder()
                .products(Arrays.asList(
                        PopularProductView.builder()
                                .productId(10L)
                                .productName("인기 상품 1")
                                .price(100000L)
                                .totalStock(200)
                                .status("판매 중")
                                .orderCount3Days(2000L)
                                .rank(1)
                                .createdAt(LocalDateTime.now().minusDays(5))
                                .build(),
                        PopularProductView.builder()
                                .productId(11L)
                                .productName("인기 상품 2")
                                .price(90000L)
                                .totalStock(180)
                                .status("판매 중")
                                .orderCount3Days(1800L)
                                .rank(2)
                                .createdAt(LocalDateTime.now().minusDays(3))
                                .build(),
                        PopularProductView.builder()
                                .productId(12L)
                                .productName("인기 상품 3")
                                .price(85000L)
                                .totalStock(150)
                                .status("판매 중")
                                .orderCount3Days(1600L)
                                .rank(3)
                                .createdAt(LocalDateTime.now().minusDays(1))
                                .build()
                ))
                .build();

        when(popularProductService.getPopularProducts())
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(3)))
                .andExpect(jsonPath("$.products[0].rank").value(1))
                .andExpect(jsonPath("$.products[1].rank").value(2))
                .andExpect(jsonPath("$.products[2].rank").value(3));

        verify(popularProductService, times(1)).getPopularProducts();
    }

    @Test
    @DisplayName("인기 상품 조회 - 성공 (1개 상품)")
    void testGetPopularProducts_Success_SingleProduct() throws Exception {
        // Given
        PopularProductListResponse response = PopularProductListResponse.builder()
                .products(Collections.singletonList(
                        PopularProductView.builder()
                                .productId(20L)
                                .productName("유일한 인기 상품")
                                .price(150000L)
                                .totalStock(100)
                                .status("판매 중")
                                .orderCount3Days(3000L)
                                .rank(1)
                                .createdAt(LocalDateTime.now())
                                .build()
                ))
                .build();

        when(popularProductService.getPopularProducts())
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(1)))
                .andExpect(jsonPath("$.products[0].rank").value(1));

        verify(popularProductService, times(1)).getPopularProducts();
    }

    @Test
    @DisplayName("인기 상품 조회 - 성공 (빈 결과)")
    void testGetPopularProducts_Success_EmptyResult() throws Exception {
        // Given
        PopularProductListResponse response = PopularProductListResponse.builder()
                .products(Collections.emptyList())
                .build();

        when(popularProductService.getPopularProducts())
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products", hasSize(0)));

        verify(popularProductService, times(1)).getPopularProducts();
    }

    @Test
    @DisplayName("인기 상품 조회 - 성공 (품절 상품 포함)")
    void testGetPopularProducts_Success_IncludingSoldOut() throws Exception {
        // Given
        PopularProductListResponse response = PopularProductListResponse.builder()
                .products(Arrays.asList(
                        PopularProductView.builder()
                                .productId(30L)
                                .productName("인기 상품")
                                .price(50000L)
                                .totalStock(100)
                                .status("판매 중")
                                .orderCount3Days(1500L)
                                .rank(1)
                                .createdAt(LocalDateTime.now().minusDays(1))
                                .build(),
                        PopularProductView.builder()
                                .productId(31L)
                                .productName("품절된 인기 상품")
                                .price(45000L)
                                .totalStock(0)
                                .status("품절")
                                .orderCount3Days(1300L)
                                .rank(2)
                                .createdAt(LocalDateTime.now().minusDays(2))
                                .build()
                ))
                .build();

        when(popularProductService.getPopularProducts())
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(2)))
                .andExpect(jsonPath("$.products[1].status").value("품절"))
                .andExpect(jsonPath("$.products[1].total_stock").value(0));

        verify(popularProductService, times(1)).getPopularProducts();
    }

    // ========== 응답 포맷 검증 ==========

    @Test
    @DisplayName("인기 상품 조회 - 응답 포맷 검증")
    void testGetPopularProducts_ResponseFormatValidation() throws Exception {
        // Given
        when(popularProductService.getPopularProducts())
                .thenReturn(PopularProductListResponse.builder()
                        .products(Collections.singletonList(
                                PopularProductView.builder()
                                        .productId(1L)
                                        .productName("테스트 상품")
                                        .price(100000L)
                                        .totalStock(100)
                                        .status("판매 중")
                                        .orderCount3Days(500L)
                                        .rank(1)
                                        .createdAt(LocalDateTime.now())
                                        .build()
                        ))
                        .build());

        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap())
                .andExpect(jsonPath("$.products").isArray());

        verify(popularProductService, times(1)).getPopularProducts();
    }

    @Test
    @DisplayName("인기 상품 조회 - 상품 필드 검증")
    void testGetPopularProducts_ProductFieldValidation() throws Exception {
        // Given
        when(popularProductService.getPopularProducts())
                .thenReturn(PopularProductListResponse.builder()
                        .products(Collections.singletonList(
                                PopularProductView.builder()
                                        .productId(1L)
                                        .productName("테스트 상품")
                                        .price(100000L)
                                        .totalStock(100)
                                        .status("판매 중")
                                        .orderCount3Days(500L)
                                        .rank(1)
                                        .createdAt(LocalDateTime.now())
                                        .build()
                        ))
                        .build());

        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0]").isMap())
                .andExpect(jsonPath("$.products[0].product_id").isNumber())
                .andExpect(jsonPath("$.products[0].product_name").isString())
                .andExpect(jsonPath("$.products[0].price").isNumber())
                .andExpect(jsonPath("$.products[0].total_stock").isNumber())
                .andExpect(jsonPath("$.products[0].status").isString())
                .andExpect(jsonPath("$.products[0].order_count_3days").isNumber())
                .andExpect(jsonPath("$.products[0].rank").isNumber());

        verify(popularProductService, times(1)).getPopularProducts();
    }

    @Test
    @DisplayName("인기 상품 조회 - 순위 정렬 검증 (내림차순)")
    void testGetPopularProducts_RankSortValidation() throws Exception {
        // Given - 판매량이 높을수록 rank가 낮아야 함 (1~5)
        when(popularProductService.getPopularProducts())
                .thenReturn(PopularProductListResponse.builder()
                        .products(Arrays.asList(
                                PopularProductView.builder()
                                        .productId(1L)
                                        .productName("1위")
                                        .price(100000L)
                                        .totalStock(100)
                                        .status("판매 중")
                                        .orderCount3Days(5000L)  // 최고 판매량
                                        .rank(1)
                                        .createdAt(LocalDateTime.now())
                                        .build(),
                                PopularProductView.builder()
                                        .productId(2L)
                                        .productName("2위")
                                        .price(90000L)
                                        .totalStock(100)
                                        .status("판매 중")
                                        .orderCount3Days(4000L)
                                        .rank(2)
                                        .createdAt(LocalDateTime.now())
                                        .build(),
                                PopularProductView.builder()
                                        .productId(3L)
                                        .productName("3위")
                                        .price(80000L)
                                        .totalStock(100)
                                        .status("판매 중")
                                        .orderCount3Days(3000L)
                                        .rank(3)
                                        .createdAt(LocalDateTime.now())
                                        .build()
                        ))
                        .build());

        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].rank").value(1))
                .andExpect(jsonPath("$.products[0].order_count_3days").value(5000L))
                .andExpect(jsonPath("$.products[1].rank").value(2))
                .andExpect(jsonPath("$.products[1].order_count_3days").value(4000L))
                .andExpect(jsonPath("$.products[2].rank").value(3))
                .andExpect(jsonPath("$.products[2].order_count_3days").value(3000L));

        verify(popularProductService, times(1)).getPopularProducts();
    }

    @Test
    @DisplayName("인기 상품 조회 - 상품 가격 검증")
    void testGetPopularProducts_PriceRangeValidation() throws Exception {
        // Given
        when(popularProductService.getPopularProducts())
                .thenReturn(PopularProductListResponse.builder()
                        .products(Arrays.asList(
                                PopularProductView.builder()
                                        .productId(1L)
                                        .productName("고가 상품")
                                        .price(999999L)
                                        .totalStock(10)
                                        .status("판매 중")
                                        .orderCount3Days(100L)
                                        .rank(1)
                                        .createdAt(LocalDateTime.now())
                                        .build(),
                                PopularProductView.builder()
                                        .productId(2L)
                                        .productName("저가 상품")
                                        .price(1000L)
                                        .totalStock(1000)
                                        .status("판매 중")
                                        .orderCount3Days(200L)
                                        .rank(2)
                                        .createdAt(LocalDateTime.now())
                                        .build()
                        ))
                        .build());

        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].price").value(999999L))
                .andExpect(jsonPath("$.products[1].price").value(1000L));

        verify(popularProductService, times(1)).getPopularProducts();
    }

    @Test
    @DisplayName("인기 상품 조회 - 재고 다양성 검증")
    void testGetPopularProducts_StockDiversityValidation() throws Exception {
        // Given - 재고 수량이 다양한 경우
        when(popularProductService.getPopularProducts())
                .thenReturn(PopularProductListResponse.builder()
                        .products(Arrays.asList(
                                PopularProductView.builder()
                                        .productId(1L)
                                        .productName("재고 많음")
                                        .price(50000L)
                                        .totalStock(10000)
                                        .status("판매 중")
                                        .orderCount3Days(500L)
                                        .rank(1)
                                        .createdAt(LocalDateTime.now())
                                        .build(),
                                PopularProductView.builder()
                                        .productId(2L)
                                        .productName("재고 적음")
                                        .price(50000L)
                                        .totalStock(1)
                                        .status("판매 중")
                                        .orderCount3Days(400L)
                                        .rank(2)
                                        .createdAt(LocalDateTime.now())
                                        .build()
                        ))
                        .build());

        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].total_stock").value(10000))
                .andExpect(jsonPath("$.products[1].total_stock").value(1));

        verify(popularProductService, times(1)).getPopularProducts();
    }
}
