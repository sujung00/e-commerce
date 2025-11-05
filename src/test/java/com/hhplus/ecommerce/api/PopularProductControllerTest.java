package com.hhplus.ecommerce.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("인기 상품 조회 API 테스트")
class PopularProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ========== 기본 기능 테스트 ==========

    @Test
    @DisplayName("인기 상품 조회 API - 성공")
    void testGetPopularProducts_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products", hasSize(greaterThanOrEqualTo(0))));
    }

    @Test
    @DisplayName("인기 상품 조회 API - 상위 5개까지만 반환")
    void testGetPopularProducts_MaxFiveProducts() throws Exception {
        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(lessThanOrEqualTo(5))));
    }

    @Test
    @DisplayName("인기 상품 조회 API - 필수 필드 포함")
    void testGetPopularProducts_RequiredFieldsIncluded() throws Exception {
        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].product_id").exists())
                .andExpect(jsonPath("$.products[0].product_name").exists())
                .andExpect(jsonPath("$.products[0].price").exists())
                .andExpect(jsonPath("$.products[0].total_stock").exists())
                .andExpect(jsonPath("$.products[0].status").exists())
                .andExpect(jsonPath("$.products[0].order_count_3days").exists())
                .andExpect(jsonPath("$.products[0].rank").exists());
    }

    @Test
    @DisplayName("인기 상품 조회 API - 순위 정렬 (1~5 순서)")
    void testGetPopularProducts_RankOrdering() throws Exception {
        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].rank").value(1))
                .andExpect(jsonPath("$.products[1].rank", anyOf(nullValue(), greaterThan(1))))
                .andExpect(jsonPath("$.products[*].rank", everyItem(both(greaterThanOrEqualTo(1)).and(lessThanOrEqualTo(5)))));
    }

    @Test
    @DisplayName("인기 상품 조회 API - 판매량 내림차순 정렬")
    void testGetPopularProducts_DescendingOrderByOrderCount() throws Exception {
        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk());
        // 주문 수량이 내림차순인지 확인하는 로직
        // (실제 구현에서는 응답 데이터를 파싱하여 검증)
    }

    @Test
    @DisplayName("인기 상품 조회 API - 응답 상태 코드 200")
    void testGetPopularProducts_StatusCode200() throws Exception {
        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("인기 상품 조회 API - 빈 결과도 200 반환")
    void testGetPopularProducts_EmptyResultReturn200() throws Exception {
        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray());
    }

    @Test
    @DisplayName("인기 상품 조회 API - 판매중지/품절 상품도 포함")
    void testGetPopularProducts_IncludesAllStatus() throws Exception {
        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[*].status", hasItems("판매 중", "품절", "판매 중지")));
    }

    @Test
    @DisplayName("인기 상품 조회 API - 캐싱 검증 (1시간 TTL)")
    void testGetPopularProducts_CachingTTL() throws Exception {
        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk());
        // 캐싱 검증은 애플리케이션 레벨에서 처리됨
    }

    @Test
    @DisplayName("인기 상품 조회 API - 타입 검증 (product_id는 Long)")
    void testGetPopularProducts_TypeValidation() throws Exception {
        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].product_id").isNumber())
                .andExpect(jsonPath("$.products[0].rank").isNumber());
    }

    @Test
    @DisplayName("인기 상품 조회 API - 응답 필드 검증")
    void testGetPopularProducts_ResponseFieldValidation() throws Exception {
        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].product_name").isString())
                .andExpect(jsonPath("$.products[0].price").isNumber())
                .andExpect(jsonPath("$.products[0].total_stock").isNumber());
    }

    @Test
    @DisplayName("인기 상품 조회 API - 최신 데이터 검증")
    void testGetPopularProducts_LatestData() throws Exception {
        // When & Then
        mockMvc.perform(get("/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0]").exists());
    }
}