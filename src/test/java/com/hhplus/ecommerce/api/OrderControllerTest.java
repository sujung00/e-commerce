package com.hhplus.ecommerce.presentation.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("주문 API 테스트")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ========== 주문 생성 ==========

    @Test
    @DisplayName("주문 생성 - 성공 (쿠폰 없음)")
    void testCreateOrder_Success_WithoutCoupon() throws Exception {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(
                Arrays.asList(
                        new OrderItem(1L, 101L, 2),
                        new OrderItem(2L, 201L, 1)
                ),
                null
        );
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order_id").exists())
                .andExpect(jsonPath("$.user_id").exists())
                .andExpect(jsonPath("$.order_status").value("COMPLETED"))
                .andExpect(jsonPath("$.subtotal").exists())
                .andExpect(jsonPath("$.coupon_discount").value(0))
                .andExpect(jsonPath("$.coupon_id").doesNotExist())
                .andExpect(jsonPath("$.final_amount").exists())
                .andExpect(jsonPath("$.order_items").isArray())
                .andExpect(jsonPath("$.order_items[0].order_item_id").exists())
                .andExpect(jsonPath("$.order_items[0].product_id").value(1))
                .andExpect(jsonPath("$.order_items[0].product_name").exists())
                .andExpect(jsonPath("$.order_items[0].option_id").value(101))
                .andExpect(jsonPath("$.order_items[0].option_name").exists())
                .andExpect(jsonPath("$.order_items[0].quantity").value(2))
                .andExpect(jsonPath("$.order_items[0].unit_price").exists())
                .andExpect(jsonPath("$.created_at").exists());
    }

    @Test
    @DisplayName("주문 생성 - 성공 (쿠폰 적용)")
    void testCreateOrder_Success_WithCoupon() throws Exception {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(
                Arrays.asList(
                        new OrderItem(1L, 101L, 2)
                ),
                1L
        );
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order_status").value("COMPLETED"))
                .andExpect(jsonPath("$.coupon_discount").exists())
                .andExpect(jsonPath("$.coupon_id").value(1));
    }

    @Test
    @DisplayName("주문 생성 - 성공 (여러 상품)")
    void testCreateOrder_Success_MultipleItems() throws Exception {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(
                Arrays.asList(
                        new OrderItem(1L, 101L, 2),
                        new OrderItem(2L, 201L, 1),
                        new OrderItem(5L, 501L, 3)
                ),
                null
        );
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order_items.length()").value(3));
    }

    @Test
    @DisplayName("주문 생성 - 실패 (아이템 없음)")
    void testCreateOrder_Failed_EmptyItems() throws Exception {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(Arrays.asList(), null);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("주문 생성 - 실패 (상품을 찾을 수 없음)")
    void testCreateOrder_Failed_ProductNotFound() throws Exception {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(
                Arrays.asList(new OrderItem(999L, 101L, 2)),
                null
        );
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("주문 생성 - 실패 (옵션을 찾을 수 없음)")
    void testCreateOrder_Failed_OptionNotFound() throws Exception {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(
                Arrays.asList(new OrderItem(1L, 999L, 2)),
                null
        );
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("OPTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("주문 생성 - 실패 (상품과 옵션 불일치)")
    void testCreateOrder_Failed_InvalidProductOption() throws Exception {
        // Given - 상품1의 옵션이 아닌 다른 상품의 옵션 사용
        CreateOrderRequest request = new CreateOrderRequest(
                Arrays.asList(new OrderItem(1L, 201L, 2)),  // 상품1에 상품2의 옵션 사용
                null
        );
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_PRODUCT_OPTION"));
    }

    @Test
    @DisplayName("주문 생성 - 실패 (재고 부족)")
    void testCreateOrder_Failed_InsufficientStock() throws Exception {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(
                Arrays.asList(new OrderItem(1L, 101L, 10000)),  // 재고 부족
                null
        );
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("ERR-001"))
                .andExpect(jsonPath("$.error_message").value(containsString("재고")));
    }

    @Test
    @DisplayName("주문 생성 - 실패 (잔액 부족)")
    void testCreateOrder_Failed_InsufficientBalance() throws Exception {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(
                Arrays.asList(new OrderItem(2L, 201L, 1000)),  // 매우 비싼 금액 × 많은 수량
                null
        );
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("ERR-002"))
                .andExpect(jsonPath("$.error_message").value(containsString("잔액")));
    }

    @Test
    @DisplayName("주문 생성 - 실패 (유효하지 않은 쿠폰)")
    void testCreateOrder_Failed_InvalidCoupon() throws Exception {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(
                Arrays.asList(new OrderItem(1L, 101L, 2)),
                999L  // 존재하지 않는 쿠폰
        );
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("ERR-003"));
    }

    @Test
    @DisplayName("주문 생성 - 실패 (버전 불일치 - Race Condition)")
    void testCreateOrder_Failed_VersionMismatch() throws Exception {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(
                Arrays.asList(new OrderItem(1L, 101L, 2)),
                null
        );
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then - 동시에 같은 옵션 주문 시 버전 불일치 발생
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("ERR-004"));
    }

    // ========== 주문 상세 조회 ==========

    @Test
    @DisplayName("주문 상세 조회 - 성공")
    void testGetOrderDetail_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/orders/5001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_id").value(5001))
                .andExpect(jsonPath("$.user_id").exists())
                .andExpect(jsonPath("$.order_status").value("COMPLETED"))
                .andExpect(jsonPath("$.subtotal").exists())
                .andExpect(jsonPath("$.coupon_discount").exists())
                .andExpect(jsonPath("$.coupon_id").exists())
                .andExpect(jsonPath("$.final_amount").exists())
                .andExpect(jsonPath("$.order_items").isArray())
                .andExpect(jsonPath("$.order_items[0].order_item_id").exists())
                .andExpect(jsonPath("$.order_items[0].product_id").exists())
                .andExpect(jsonPath("$.order_items[0].product_name").exists())
                .andExpect(jsonPath("$.order_items[0].option_id").exists())
                .andExpect(jsonPath("$.order_items[0].option_name").exists())
                .andExpect(jsonPath("$.order_items[0].quantity").exists())
                .andExpect(jsonPath("$.order_items[0].unit_price").exists())
                .andExpect(jsonPath("$.created_at").exists());
    }

    @Test
    @DisplayName("주문 상세 조회 - 실패 (주문을 찾을 수 없음)")
    void testGetOrderDetail_Failed_NotFound() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/orders/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("NOT_FOUND"));
    }

    // ========== 주문 목록 조회 ==========

    @Test
    @DisplayName("주문 목록 조회 - 성공 (기본값)")
    void testGetOrders_Success_Default() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.totalPages").exists())
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    @DisplayName("주문 목록 조회 - 성공 (페이지네이션)")
    void testGetOrders_Success_WithPagination() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/orders")
                .param("page", "1")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @DisplayName("주문 목록 조회 - 성공 (상태 필터)")
    void testGetOrders_Success_WithStatusFilter() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/orders")
                .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].order_status").value("COMPLETED"));
    }

    @Test
    @DisplayName("주문 목록 조회 - 성공 (빈 목록도 200)")
    void testGetOrders_Success_EmptyList() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/orders")
                .param("page", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ========== Request/Response DTO ==========

    static class CreateOrderRequest {
        public List<OrderItem> order_items;
        public Long coupon_id;

        public CreateOrderRequest(List<OrderItem> order_items, Long coupon_id) {
            this.order_items = order_items;
            this.coupon_id = coupon_id;
        }
    }

    static class OrderItem {
        public Long product_id;
        public Long option_id;
        public Integer quantity;

        public OrderItem(Long product_id, Long option_id, Integer quantity) {
            this.product_id = product_id;
            this.option_id = option_id;
            this.quantity = quantity;
        }
    }

}