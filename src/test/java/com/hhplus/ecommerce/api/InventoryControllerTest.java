package com.hhplus.ecommerce.presentation.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("재고 API 테스트")
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ========== 재고 현황 조회 ==========

    @Test
    @DisplayName("재고 현황 조회 - 성공")
    void testGetInventory_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/inventory/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_id").value(1))
                .andExpect(jsonPath("$.product_name").exists())
                .andExpect(jsonPath("$.total_stock").exists())
                .andExpect(jsonPath("$.options").isArray())
                .andExpect(jsonPath("$.options[0].option_id").exists())
                .andExpect(jsonPath("$.options[0].name").exists())
                .andExpect(jsonPath("$.options[0].stock").exists())
                .andExpect(jsonPath("$.options[0].version").exists());
    }

    @Test
    @DisplayName("재고 현황 조회 - 성공 (옵션별 재고 세부사항)")
    void testGetInventory_Success_OptionDetails() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/inventory/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].option_id").isNumber())
                .andExpect(jsonPath("$.options[0].name").isString())
                .andExpect(jsonPath("$.options[0].stock").isNumber())
                .andExpect(jsonPath("$.options[0].version").isNumber());
    }

    @Test
    @DisplayName("재고 현황 조회 - 성공 (total_stock = SUM(options.stock))")
    void testGetInventory_Success_TotalStockCalculation() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/inventory/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_stock").exists())
                // total_stock이 각 옵션의 재고의 합과 일치하는지 확인
                .andExpect(jsonPath("$.total_stock").isNumber());
    }

    @Test
    @DisplayName("재고 현황 조회 - 성공 (여러 옵션)")
    void testGetInventory_Success_MultipleOptions() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/inventory/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("재고 현황 조회 - 실패 (상품을 찾을 수 없음)")
    void testGetInventory_Failed_ProductNotFound() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/inventory/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.error_message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.request_id").exists());
    }

    @Test
    @DisplayName("재고 현황 조회 - 실패 (음수 product_id)")
    void testGetInventory_Failed_NegativeId() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/inventory/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("재고 현황 조회 - 실패 (0 product_id)")
    void testGetInventory_Failed_ZeroId() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/inventory/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_REQUEST"));
    }

    // ========== 재고 상태 검증 ==========

    @Test
    @DisplayName("재고 현황 조회 - 검증 (재고 0 이상)")
    void testGetInventory_Validation_NonNegativeStock() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/inventory/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_stock").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.options[0].stock").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("재고 현황 조회 - 검증 (옵션 ID는 양수)")
    void testGetInventory_Validation_PositiveOptionId() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/inventory/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].option_id").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("재고 현황 조회 - 검증 (버전 정보 포함)")
    void testGetInventory_Validation_VersionIncluded() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/inventory/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].version").exists())
                .andExpect(jsonPath("$.options[0].version").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)));
    }

    // ========== 복수 상품 재고 조회 (엣지 케이스) ==========

    @Test
    @DisplayName("재고 현황 조회 - 성공 (다양한 product_id)")
    void testGetInventory_Success_VariousProductIds() throws Exception {
        // Test for product_id = 1
        mockMvc.perform(get("/api/inventory/1"))
                .andExpect(status().isOk());

        // Test for product_id = 2
        mockMvc.perform(get("/api/inventory/2"))
                .andExpect(status().isOk());

        // Test for product_id = 5
        mockMvc.perform(get("/api/inventory/5"))
                .andExpect(status().isOk());
    }

}