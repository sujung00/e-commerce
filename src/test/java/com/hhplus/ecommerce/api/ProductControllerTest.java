package com.hhplus.ecommerce.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("상품 API 테스트")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ========== 상품 목록 조회 ==========

    @Test
    @DisplayName("상품 목록 조회 - 성공 (기본값)")
    void testGetProducts_Success_Default() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.totalPages").exists())
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    @DisplayName("상품 목록 조회 - 성공 (페이지네이션 파라미터)")
    void testGetProducts_Success_WithPagination() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products")
                .param("page", "1")
                .param("size", "20")
                .param("sort", "price,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @DisplayName("상품 목록 조회 - 성공 (빈 목록도 200 반환)")
    void testGetProducts_Success_EmptyList() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products")
                .param("page", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("상품 목록 조회 - 실패 (음수 페이지)")
    void testGetProducts_Failed_NegativePage() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products")
                .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error_message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.request_id").exists());
    }

    @Test
    @DisplayName("상품 목록 조회 - 실패 (페이지 크기가 0)")
    void testGetProducts_Failed_ZeroSize() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products")
                .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error_message").value(containsString("1")));
    }

    @Test
    @DisplayName("상품 목록 조회 - 실패 (페이지 크기 초과)")
    void testGetProducts_Failed_ExceedMaxSize() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products")
                .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error_message").value(containsString("100")));
    }

    @Test
    @DisplayName("상품 목록 조회 - 실패 (존재하지 않는 정렬 필드)")
    void testGetProducts_Failed_InvalidSortField() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products")
                .param("sort", "invalid_field,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_REQUEST"));
    }

    // ========== 상품 상세 조회 ==========

    @Test
    @DisplayName("상품 상세 조회 - 성공")
    void testGetProductDetail_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_id").value(1))
                .andExpect(jsonPath("$.product_name").exists())
                .andExpect(jsonPath("$.description").exists())
                .andExpect(jsonPath("$.price").exists())
                .andExpect(jsonPath("$.total_stock").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.options").isArray())
                .andExpect(jsonPath("$.created_at").exists());
    }

    @Test
    @DisplayName("상품 상세 조회 - 성공 (옵션 포함)")
    void testGetProductDetail_Success_WithOptions() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].option_id").exists())
                .andExpect(jsonPath("$.options[0].name").exists())
                .andExpect(jsonPath("$.options[0].stock").exists())
                .andExpect(jsonPath("$.options[0].version").exists());
    }

    @Test
    @DisplayName("상품 상세 조회 - 실패 (상품을 찾을 수 없음)")
    void testGetProductDetail_Failed_NotFound() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.error_message").value(containsString("999")))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.request_id").exists());
    }

    @Test
    @DisplayName("상품 상세 조회 - 실패 (음수 product_id)")
    void testGetProductDetail_Failed_NegativeId() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error_message").value(containsString("양수")));
    }

    @Test
    @DisplayName("상품 상세 조회 - 실패 (0 product_id)")
    void testGetProductDetail_Failed_ZeroId() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error_message").value(containsString("양수")));
    }

    // ========== 인기 상품 조회 ==========

    @Test
    @DisplayName("인기 상품 조회 - 성공")
    void testGetPopularProducts_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products[0].product_id").exists())
                .andExpect(jsonPath("$.products[0].product_name").exists())
                .andExpect(jsonPath("$.products[0].price").exists())
                .andExpect(jsonPath("$.products[0].total_stock").exists())
                .andExpect(jsonPath("$.products[0].status").exists())
                .andExpect(jsonPath("$.products[0].order_count_3days").exists())
                .andExpect(jsonPath("$.products[0].rank").exists());
    }

    @Test
    @DisplayName("인기 상품 조회 - 성공 (최대 5개)")
    void testGetPopularProducts_Success_MaxFive() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(5)));
    }

    @Test
    @DisplayName("인기 상품 조회 - 성공 (빈 결과도 200 반환)")
    void testGetPopularProducts_Success_EmptyResult() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray());
    }

    @Test
    @DisplayName("인기 상품 조회 - 성공 (순위 정렬)")
    void testGetPopularProducts_Success_RankOrder() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].rank").value(1))
                .andExpect(jsonPath("$.products[1].rank").value(2));
    }

}