package com.hhplus.ecommerce.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("장바구니 API 테스트")
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ========== 장바구니 조회 ==========

    @Test
    @DisplayName("장바구니 조회 - 성공")
    void testGetCart_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/carts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cart_id").exists())
                .andExpect(jsonPath("$.user_id").exists())
                .andExpect(jsonPath("$.total_items").exists())
                .andExpect(jsonPath("$.total_price").exists())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.updated_at").exists());
    }

    @Test
    @DisplayName("장바구니 조회 - 성공 (빈 장바구니도 200)")
    void testGetCart_Success_EmptyCart() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/carts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_items").value(0))
                .andExpect(jsonPath("$.total_price").value(0));
    }

    @Test
    @DisplayName("장바구니 조회 - 성공 (아이템 포함)")
    void testGetCart_Success_WithItems() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/carts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].cart_item_id").exists())
                .andExpect(jsonPath("$.items[0].product_id").exists())
                .andExpect(jsonPath("$.items[0].product_name").exists())
                .andExpect(jsonPath("$.items[0].option_id").exists())
                .andExpect(jsonPath("$.items[0].option_name").exists())
                .andExpect(jsonPath("$.items[0].quantity").exists())
                .andExpect(jsonPath("$.items[0].unit_price").exists())
                .andExpect(jsonPath("$.items[0].subtotal").exists());
    }

    // ========== 장바구니 아이템 추가 ==========

    @Test
    @DisplayName("장바구니 아이템 추가 - 성공")
    void testAddCartItem_Success() throws Exception {
        // Given
        String requestBody = objectMapper.writeValueAsString(
                new AddCartItemRequest(1L, 101L, 2)
        );

        // When & Then
        mockMvc.perform(post("/api/carts/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cart_item_id").exists())
                .andExpect(jsonPath("$.cart_id").exists())
                .andExpect(jsonPath("$.product_id").value(1))
                .andExpect(jsonPath("$.product_name").exists())
                .andExpect(jsonPath("$.option_id").value(101))
                .andExpect(jsonPath("$.option_name").exists())
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.unit_price").exists())
                .andExpect(jsonPath("$.subtotal").exists())
                .andExpect(jsonPath("$.created_at").exists());
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 성공 (최소 수량)")
    void testAddCartItem_Success_MinQuantity() throws Exception {
        // Given
        String requestBody = objectMapper.writeValueAsString(
                new AddCartItemRequest(1L, 101L, 1)
        );

        // When & Then
        mockMvc.perform(post("/api/carts/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(1));
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 성공 (최대 수량)")
    void testAddCartItem_Success_MaxQuantity() throws Exception {
        // Given
        String requestBody = objectMapper.writeValueAsString(
                new AddCartItemRequest(1L, 101L, 1000)
        );

        // When & Then
        mockMvc.perform(post("/api/carts/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(1000));
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 실패 (상품 없음)")
    void testAddCartItem_Failed_ProductNotFound() throws Exception {
        // Given
        String requestBody = objectMapper.writeValueAsString(
                new AddCartItemRequest(999L, 101L, 2)
        );

        // When & Then
        mockMvc.perform(post("/api/carts/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 실패 (옵션 없음)")
    void testAddCartItem_Failed_OptionNotFound() throws Exception {
        // Given
        String requestBody = objectMapper.writeValueAsString(
                new AddCartItemRequest(1L, 999L, 2)
        );

        // When & Then
        mockMvc.perform(post("/api/carts/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("OPTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 실패 (수량 0)")
    void testAddCartItem_Failed_ZeroQuantity() throws Exception {
        // Given
        String requestBody = objectMapper.writeValueAsString(
                new AddCartItemRequest(1L, 101L, 0)
        );

        // When & Then
        mockMvc.perform(post("/api/carts/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error_message").value(containsString("1")));
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 실패 (수량 초과)")
    void testAddCartItem_Failed_ExceedMaxQuantity() throws Exception {
        // Given
        String requestBody = objectMapper.writeValueAsString(
                new AddCartItemRequest(1L, 101L, 1001)
        );

        // When & Then
        mockMvc.perform(post("/api/carts/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error_message").value(containsString("1000")));
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 실패 (product_id 누락)")
    void testAddCartItem_Failed_MissingProductId() throws Exception {
        // Given
        String requestBody = "{\"option_id\": 101, \"quantity\": 2}";

        // When & Then
        mockMvc.perform(post("/api/carts/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 실패 (option_id 누락)")
    void testAddCartItem_Failed_MissingOptionId() throws Exception {
        // Given
        String requestBody = "{\"product_id\": 1, \"quantity\": 2}";

        // When & Then
        mockMvc.perform(post("/api/carts/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    // ========== 장바구니 아이템 수량 수정 ==========

    @Test
    @DisplayName("장바구니 아이템 수량 수정 - 성공")
    void testUpdateCartItem_Success() throws Exception {
        // Given
        String requestBody = objectMapper.writeValueAsString(
                new UpdateCartItemRequest(5)
        );

        // When & Then
        mockMvc.perform(put("/api/carts/items/1001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cart_item_id").value(1001))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.subtotal").exists())
                .andExpect(jsonPath("$.updated_at").exists());
    }

    @Test
    @DisplayName("장바구니 아이템 수량 수정 - 성공 (최소 수량)")
    void testUpdateCartItem_Success_MinQuantity() throws Exception {
        // Given
        String requestBody = objectMapper.writeValueAsString(
                new UpdateCartItemRequest(1)
        );

        // When & Then
        mockMvc.perform(put("/api/carts/items/1001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(1));
    }

    @Test
    @DisplayName("장바구니 아이템 수량 수정 - 성공 (최대 수량)")
    void testUpdateCartItem_Success_MaxQuantity() throws Exception {
        // Given
        String requestBody = objectMapper.writeValueAsString(
                new UpdateCartItemRequest(1000)
        );

        // When & Then
        mockMvc.perform(put("/api/carts/items/1001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(1000));
    }

    @Test
    @DisplayName("장바구니 아이템 수량 수정 - 실패 (아이템을 찾을 수 없음)")
    void testUpdateCartItem_Failed_NotFound() throws Exception {
        // Given
        String requestBody = objectMapper.writeValueAsString(
                new UpdateCartItemRequest(5)
        );

        // When & Then
        mockMvc.perform(put("/api/carts/items/9999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("장바구니 아이템 수량 수정 - 실패 (수량 0)")
    void testUpdateCartItem_Failed_ZeroQuantity() throws Exception {
        // Given
        String requestBody = objectMapper.writeValueAsString(
                new UpdateCartItemRequest(0)
        );

        // When & Then
        mockMvc.perform(put("/api/carts/items/1001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("장바구니 아이템 수량 수정 - 실패 (수량 초과)")
    void testUpdateCartItem_Failed_ExceedMaxQuantity() throws Exception {
        // Given
        String requestBody = objectMapper.writeValueAsString(
                new UpdateCartItemRequest(1001)
        );

        // When & Then
        mockMvc.perform(put("/api/carts/items/1001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_REQUEST"));
    }

    // ========== 장바구니 아이템 제거 ==========

    @Test
    @DisplayName("장바구니 아이템 제거 - 성공")
    void testDeleteCartItem_Success() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/carts/items/1001"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("장바구니 아이템 제거 - 실패 (아이템을 찾을 수 없음)")
    void testDeleteCartItem_Failed_NotFound() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/carts/items/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("NOT_FOUND"));
    }

    // ========== Request/Response DTO ==========

    static class AddCartItemRequest {
        public Long product_id;
        public Long option_id;
        public Integer quantity;

        public AddCartItemRequest(Long product_id, Long option_id, Integer quantity) {
            this.product_id = product_id;
            this.option_id = option_id;
            this.quantity = quantity;
        }
    }

    static class UpdateCartItemRequest {
        public Integer quantity;

        public UpdateCartItemRequest(Integer quantity) {
            this.quantity = quantity;
        }
    }

}