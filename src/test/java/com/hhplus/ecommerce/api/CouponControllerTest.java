package com.hhplus.ecommerce.presentation.product;

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
@DisplayName("쿠폰 API 테스트")
class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ========== 쿠폰 발급 ==========

    @Test
    @DisplayName("쿠폰 발급 - 성공 (퍼센트 할인)")
    void testIssueCoupon_Success_Percentage() throws Exception {
        // Given
        IssueCouponRequest request = new IssueCouponRequest(1L);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/coupons/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_coupon_id").exists())
                .andExpect(jsonPath("$.user_id").exists())
                .andExpect(jsonPath("$.coupon_id").value(1))
                .andExpect(jsonPath("$.coupon_name").exists())
                .andExpect(jsonPath("$.discount_type").value("PERCENTAGE"))
                .andExpect(jsonPath("$.discount_rate").value(0.10))
                .andExpect(jsonPath("$.discount_amount").doesNotExist())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.issued_at").exists())
                .andExpect(jsonPath("$.valid_from").exists())
                .andExpect(jsonPath("$.valid_until").exists());
    }

    @Test
    @DisplayName("쿠폰 발급 - 성공 (고정 금액 할인)")
    void testIssueCoupon_Success_FixedAmount() throws Exception {
        // Given
        IssueCouponRequest request = new IssueCouponRequest(2L);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/coupons/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coupon_id").value(2))
                .andExpect(jsonPath("$.discount_type").value("FIXED_AMOUNT"))
                .andExpect(jsonPath("$.discount_amount").exists())
                .andExpect(jsonPath("$.discount_rate").doesNotExist())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (쿠폰을 찾을 수 없음)")
    void testIssueCoupon_Failed_CouponNotFound() throws Exception {
        // Given
        IssueCouponRequest request = new IssueCouponRequest(999L);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/coupons/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("COUPON_NOT_FOUND"));
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (쿠폰이 모두 소진됨)")
    void testIssueCoupon_Failed_CouponExhausted() throws Exception {
        // Given
        IssueCouponRequest request = new IssueCouponRequest(1L);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/coupons/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("ERR-003"))
                .andExpect(jsonPath("$.error_message").value(containsString("소진")));
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (유효기간 벗어남)")
    void testIssueCoupon_Failed_OutOfValidPeriod() throws Exception {
        // Given - 유효기간이 지난 쿠폰
        IssueCouponRequest request = new IssueCouponRequest(1L);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/coupons/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("ERR-003"))
                .andExpect(jsonPath("$.error_message").value(containsString("유효기간")));
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (이미 발급받은 쿠폰)")
    void testIssueCoupon_Failed_AlreadyIssued() throws Exception {
        // Given
        IssueCouponRequest request = new IssueCouponRequest(1L);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then - 같은 쿠폰을 두 번 발급 시도
        mockMvc.perform(post("/api/coupons/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("ERR-003"))
                .andExpect(jsonPath("$.error_message").value(containsString("발급")));
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (비활성화된 쿠폰)")
    void testIssueCoupon_Failed_InactiveCoupon() throws Exception {
        // Given
        IssueCouponRequest request = new IssueCouponRequest(1L);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/coupons/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("ERR-003"))
                .andExpect(jsonPath("$.error_message").value(containsString("비활성화")));
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (coupon_id 누락)")
    void testIssueCoupon_Failed_MissingCouponId() throws Exception {
        // Given
        String requestBody = "{}";

        // When & Then
        mockMvc.perform(post("/api/coupons/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    // ========== 사용자 보유 쿠폰 조회 ==========

    @Test
    @DisplayName("사용자 보유 쿠폰 조회 - 성공 (ACTIVE 기본)")
    void testGetIssuedCoupons_Success_DefaultActive() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/coupons/issued"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_coupons").isArray())
                .andExpect(jsonPath("$.user_coupons[0].user_coupon_id").exists())
                .andExpect(jsonPath("$.user_coupons[0].coupon_id").exists())
                .andExpect(jsonPath("$.user_coupons[0].coupon_name").exists())
                .andExpect(jsonPath("$.user_coupons[0].discount_type").exists())
                .andExpect(jsonPath("$.user_coupons[0].discount_rate").exists())
                .andExpect(jsonPath("$.user_coupons[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.user_coupons[0].issued_at").exists())
                .andExpect(jsonPath("$.user_coupons[0].used_at").isEmpty())
                .andExpect(jsonPath("$.user_coupons[0].valid_from").exists())
                .andExpect(jsonPath("$.user_coupons[0].valid_until").exists());
    }

    @Test
    @DisplayName("사용자 보유 쿠폰 조회 - 성공 (ACTIVE 필터)")
    void testGetIssuedCoupons_Success_FilterActive() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/coupons/issued")
                .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_coupons").isArray())
                .andExpect(jsonPath("$.user_coupons[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("사용자 보유 쿠폰 조회 - 성공 (USED 필터)")
    void testGetIssuedCoupons_Success_FilterUsed() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/coupons/issued")
                .param("status", "USED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_coupons").isArray())
                .andExpect(jsonPath("$.user_coupons[0].status").value("USED"))
                .andExpect(jsonPath("$.user_coupons[0].used_at").exists());
    }

    @Test
    @DisplayName("사용자 보유 쿠폰 조회 - 성공 (EXPIRED 필터)")
    void testGetIssuedCoupons_Success_FilterExpired() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/coupons/issued")
                .param("status", "EXPIRED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_coupons").isArray())
                .andExpect(jsonPath("$.user_coupons[0].status").value("EXPIRED"));
    }

    @Test
    @DisplayName("사용자 보유 쿠폰 조회 - 성공 (쿠폰이 없는 경우)")
    void testGetIssuedCoupons_Success_EmptyList() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/coupons/issued"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_coupons").isArray());
    }

    // ========== 사용 가능한 쿠폰 조회 ==========

    @Test
    @DisplayName("사용 가능한 쿠폰 조회 - 성공")
    void testGetAvailableCoupons_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons").isArray())
                .andExpect(jsonPath("$.coupons[0].coupon_id").exists())
                .andExpect(jsonPath("$.coupons[0].coupon_name").exists())
                .andExpect(jsonPath("$.coupons[0].description").exists())
                .andExpect(jsonPath("$.coupons[0].discount_type").exists())
                .andExpect(jsonPath("$.coupons[0].valid_from").exists())
                .andExpect(jsonPath("$.coupons[0].valid_until").exists())
                .andExpect(jsonPath("$.coupons[0].remaining_qty").exists());
    }

    @Test
    @DisplayName("사용 가능한 쿠폰 조회 - 성공 (퍼센트 할인 쿠폰)")
    void testGetAvailableCoupons_Success_PercentageCoupon() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons[0].discount_type").value("PERCENTAGE"))
                .andExpect(jsonPath("$.coupons[0].discount_rate").exists());
    }

    @Test
    @DisplayName("사용 가능한 쿠폰 조회 - 성공 (고정 금액 할인 쿠폰)")
    void testGetAvailableCoupons_Success_FixedAmountCoupon() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons[1].discount_type").value("FIXED_AMOUNT"))
                .andExpect(jsonPath("$.coupons[1].discount_amount").exists());
    }

    @Test
    @DisplayName("사용 가능한 쿠폰 조회 - 성공 (빈 쿠폰 목록도 200)")
    void testGetAvailableCoupons_Success_EmptyList() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons").isArray());
    }

    // ========== Request/Response DTO ==========

    static class IssueCouponRequest {
        public Long coupon_id;

        public IssueCouponRequest(Long coupon_id) {
            this.coupon_id = coupon_id;
        }
    }

}