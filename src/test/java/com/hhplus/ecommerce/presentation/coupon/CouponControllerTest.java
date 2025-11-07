package com.hhplus.ecommerce.presentation.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.presentation.coupon.CouponController;
import com.hhplus.ecommerce.presentation.coupon.request.IssueCouponRequest;
import com.hhplus.ecommerce.presentation.coupon.response.AvailableCouponResponse;
import com.hhplus.ecommerce.presentation.coupon.response.GetAvailableCouponsResponse;
import com.hhplus.ecommerce.presentation.coupon.response.GetUserCouponsResponse;
import com.hhplus.ecommerce.presentation.coupon.response.IssueCouponResponse;
import com.hhplus.ecommerce.presentation.coupon.response.UserCouponResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.hhplus.ecommerce.common.BaseControllerTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CouponControllerTest - Presentation Layer Unit Test
 * Spring Boot 3.4+ Mockito 방식 테스트
 *
 * 테스트 대상: CouponController
 * - POST /coupons/issue - 쿠폰 발급 (선착순)
 * - GET /coupons/issued - 사용자 쿠폰 조회
 * - GET /coupons - 발급 가능한 쿠폰 조회
 *
 * 테스트 유형:
 * - 성공 케이스: 정상적인 쿠폰 발급, 조회
 * - 경계값 테스트: 빈 결과, 상태 필터링
 * - 실패 케이스: 유효하지 않은 헤더, 파라미터
 */
@ExtendWith(MockitoExtension.class)
@TestPropertySource(properties = {"spring.web.resources.add-mappings=false"})
@DisplayName("CouponController 단위 테스트")
class CouponControllerTest extends BaseControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private CouponService couponService;

    @InjectMocks
    private CouponController couponController;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_COUPON_ID = 1L;
    private static final Long TEST_USER_COUPON_ID = 100L;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        this.mockMvc = MockMvcBuilders.standaloneSetup(couponController).build();
        this.objectMapper = new ObjectMapper();
    }

    // ========== 쿠폰 발급 (POST /coupons/issue) ==========

    @Test
    @DisplayName("쿠폰 발급 - 성공")
    void testIssueCoupon_Success() throws Exception {
        // Given
        IssueCouponResponse response = IssueCouponResponse.builder()
                .userCouponId(TEST_USER_COUPON_ID)
                .userId(TEST_USER_ID)
                .couponId(TEST_COUPON_ID)
                .couponName("신규고객 할인 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .discountRate(null)
                .status("ACTIVE")
                .issuedAt(LocalDateTime.now())
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(30))
                .build();

        IssueCouponRequest request = new IssueCouponRequest();
        request.setCouponId(TEST_COUPON_ID);

        when(couponService.issueCoupon(TEST_USER_ID, TEST_COUPON_ID))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/coupons/issue")
                .header("X-USER-ID", TEST_USER_ID)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_coupon_id").value(TEST_USER_COUPON_ID))
                .andExpect(jsonPath("$.coupon_name").value("신규고객 할인 쿠폰"))
                .andExpect(jsonPath("$.discount_type").value("FIXED_AMOUNT"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(couponService, times(1)).issueCoupon(TEST_USER_ID, TEST_COUPON_ID);
    }

    @Test
    @DisplayName("쿠폰 발급 - 성공 (할인율 타입)")
    void testIssueCoupon_Success_PercentageType() throws Exception {
        // Given
        IssueCouponResponse response = IssueCouponResponse.builder()
                .userCouponId(TEST_USER_COUPON_ID)
                .userId(TEST_USER_ID)
                .couponId(2L)
                .couponName("봄 시즌 할인")
                .discountType("PERCENTAGE")
                .discountAmount(null)
                .discountRate(new BigDecimal("10.00"))
                .status("ACTIVE")
                .issuedAt(LocalDateTime.now())
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(14))
                .build();

        IssueCouponRequest request = new IssueCouponRequest();
        request.setCouponId(2L);

        when(couponService.issueCoupon(TEST_USER_ID, 2L))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/coupons/issue")
                .header("X-USER-ID", TEST_USER_ID)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discount_type").value("PERCENTAGE"));

        verify(couponService, times(1)).issueCoupon(TEST_USER_ID, 2L);
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (헤더 누락)")
    void testIssueCoupon_Failed_MissingHeader() throws Exception {
        // Given
        IssueCouponRequest request = new IssueCouponRequest();
        request.setCouponId(TEST_COUPON_ID);

        // When & Then
        mockMvc.perform(post("/coupons/issue")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (요청 본문 누락)")
    void testIssueCoupon_Failed_MissingBody() throws Exception {
        // When & Then
        mockMvc.perform(post("/coupons/issue")
                .header("X-USER-ID", TEST_USER_ID)
                .contentType("application/json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("쿠폰 발급 - 실패 (유효하지 않은 쿠폰 ID)")
    void testIssueCoupon_Failed_InvalidCouponId() throws Exception {
        // Given
        IssueCouponRequest request = new IssueCouponRequest();
        request.setCouponId(0L);

        // When & Then
        mockMvc.perform(post("/coupons/issue")
                .header("X-USER-ID", TEST_USER_ID)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ========== 사용자 쿠폰 조회 (GET /coupons/issued) ==========

    @Test
    @DisplayName("사용자 쿠폰 조회 - 성공 (기본 ACTIVE 상태)")
    void testGetUserCoupons_Success_DefaultStatus() throws Exception {
        // Given
        GetUserCouponsResponse response = GetUserCouponsResponse.builder()
                .userCoupons(Arrays.asList(
                        UserCouponResponse.builder()
                                .userCouponId(100L)
                                .couponId(1L)
                                .couponName("신규고객 할인")
                                .discountType("FIXED_AMOUNT")
                                .discountAmount(5000L)
                                .status("ACTIVE")
                                .issuedAt(LocalDateTime.now().minusDays(5))
                                .validFrom(LocalDateTime.now().minusDays(5))
                                .validUntil(LocalDateTime.now().plusDays(25))
                                .build(),
                        UserCouponResponse.builder()
                                .userCouponId(101L)
                                .couponId(2L)
                                .couponName("여름 세일")
                                .discountType("PERCENTAGE")
                                .discountRate(new BigDecimal("15.00"))
                                .status("ACTIVE")
                                .issuedAt(LocalDateTime.now().minusDays(2))
                                .validFrom(LocalDateTime.now().minusDays(2))
                                .validUntil(LocalDateTime.now().plusDays(28))
                                .build()
                ))
                .build();

        when(couponService.getUserCoupons(TEST_USER_ID, "ACTIVE"))
                .thenReturn(response.getUserCoupons());

        // When & Then
        mockMvc.perform(get("/coupons/issued")
                .header("X-USER-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_coupons").isArray())
                .andExpect(jsonPath("$.user_coupons", hasSize(2)))
                .andExpect(jsonPath("$.user_coupons[0].coupon_name").value("신규고객 할인"))
                .andExpect(jsonPath("$.user_coupons[0].status").value("ACTIVE"));

        verify(couponService, times(1)).getUserCoupons(TEST_USER_ID, "ACTIVE");
    }

    @Test
    @DisplayName("사용자 쿠폰 조회 - 성공 (USED 상태 필터링)")
    void testGetUserCoupons_Success_UsedStatus() throws Exception {
        // Given
        GetUserCouponsResponse response = GetUserCouponsResponse.builder()
                .userCoupons(Collections.singletonList(
                        UserCouponResponse.builder()
                                .userCouponId(102L)
                                .couponId(3L)
                                .couponName("이미 사용된 쿠폰")
                                .discountType("FIXED_AMOUNT")
                                .discountAmount(3000L)
                                .status("USED")
                                .issuedAt(LocalDateTime.now().minusDays(10))
                                .usedAt(LocalDateTime.now().minusDays(1))
                                .validFrom(LocalDateTime.now().minusDays(10))
                                .validUntil(LocalDateTime.now().plusDays(20))
                                .build()
                ))
                .build();

        when(couponService.getUserCoupons(TEST_USER_ID, "USED"))
                .thenReturn(response.getUserCoupons());

        // When & Then
        mockMvc.perform(get("/coupons/issued")
                .header("X-USER-ID", TEST_USER_ID)
                .param("status", "USED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_coupons", hasSize(1)))
                .andExpect(jsonPath("$.user_coupons[0].status").value("USED"));

        verify(couponService, times(1)).getUserCoupons(TEST_USER_ID, "USED");
    }

    @Test
    @DisplayName("사용자 쿠폰 조회 - 성공 (EXPIRED 상태 필터링)")
    void testGetUserCoupons_Success_ExpiredStatus() throws Exception {
        // Given
        GetUserCouponsResponse response = GetUserCouponsResponse.builder()
                .userCoupons(Collections.singletonList(
                        UserCouponResponse.builder()
                                .userCouponId(103L)
                                .couponId(4L)
                                .couponName("만료된 쿠폰")
                                .discountType("FIXED_AMOUNT")
                                .discountAmount(2000L)
                                .status("EXPIRED")
                                .issuedAt(LocalDateTime.now().minusDays(60))
                                .validFrom(LocalDateTime.now().minusDays(60))
                                .validUntil(LocalDateTime.now().minusDays(30))
                                .build()
                ))
                .build();

        when(couponService.getUserCoupons(TEST_USER_ID, "EXPIRED"))
                .thenReturn(response.getUserCoupons());

        // When & Then
        mockMvc.perform(get("/coupons/issued")
                .header("X-USER-ID", TEST_USER_ID)
                .param("status", "EXPIRED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_coupons", hasSize(1)))
                .andExpect(jsonPath("$.user_coupons[0].status").value("EXPIRED"));

        verify(couponService, times(1)).getUserCoupons(TEST_USER_ID, "EXPIRED");
    }

    @Test
    @DisplayName("사용자 쿠폰 조회 - 성공 (빈 결과)")
    void testGetUserCoupons_Success_EmptyResult() throws Exception {
        // Given
        when(couponService.getUserCoupons(TEST_USER_ID, "ACTIVE"))
                .thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/coupons/issued")
                .header("X-USER-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_coupons").isArray())
                .andExpect(jsonPath("$.user_coupons", hasSize(0)));

        verify(couponService, times(1)).getUserCoupons(TEST_USER_ID, "ACTIVE");
    }

    @Test
    @DisplayName("사용자 쿠폰 조회 - 실패 (헤더 누락)")
    void testGetUserCoupons_Failed_MissingHeader() throws Exception {
        // When & Then
        mockMvc.perform(get("/coupons/issued"))
                .andExpect(status().isBadRequest());
    }

    // ========== 발급 가능한 쿠폰 조회 (GET /coupons) ==========

    @Test
    @DisplayName("발급 가능한 쿠폰 조회 - 성공")
    void testGetAvailableCoupons_Success() throws Exception {
        // Given
        GetAvailableCouponsResponse response = GetAvailableCouponsResponse.builder()
                .coupons(Arrays.asList(
                        AvailableCouponResponse.builder()
                                .couponId(1L)
                                .couponName("신규고객 할인 쿠폰")
                                .discountType("FIXED_AMOUNT")
                                .discountAmount(5000L)
                                .validFrom(LocalDateTime.now())
                                .validUntil(LocalDateTime.now().plusDays(30))
                                .remainingQty(500)
                                .build(),
                        AvailableCouponResponse.builder()
                                .couponId(2L)
                                .couponName("봄 시즌 할인")
                                .discountType("PERCENTAGE")
                                .discountRate(new BigDecimal("10.00"))
                                .validFrom(LocalDateTime.now())
                                .validUntil(LocalDateTime.now().plusDays(14))
                                .remainingQty(1000)
                                .build()
                ))
                .build();

        when(couponService.getAvailableCoupons())
                .thenReturn(response.getCoupons());

        // When & Then
        mockMvc.perform(get("/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons").isArray())
                .andExpect(jsonPath("$.coupons", hasSize(2)))
                .andExpect(jsonPath("$.coupons[0].coupon_name").value("신규고객 할인 쿠폰"))
                .andExpect(jsonPath("$.coupons[0].discount_type").value("FIXED_AMOUNT"));

        verify(couponService, times(1)).getAvailableCoupons();
    }

    @Test
    @DisplayName("발급 가능한 쿠폰 조회 - 성공 (빈 결과)")
    void testGetAvailableCoupons_Success_EmptyResult() throws Exception {
        // Given
        when(couponService.getAvailableCoupons())
                .thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons").isArray())
                .andExpect(jsonPath("$.coupons", hasSize(0)));

        verify(couponService, times(1)).getAvailableCoupons();
    }

    // ========== 응답 포맷 검증 ==========

    @Test
    @DisplayName("쿠폰 발급 - 응답 필드 검증")
    void testIssueCoupon_ResponseFieldValidation() throws Exception {
        // Given
        IssueCouponResponse response = IssueCouponResponse.builder()
                .userCouponId(TEST_USER_COUPON_ID)
                .userId(TEST_USER_ID)
                .couponId(TEST_COUPON_ID)
                .couponName("테스트 쿠폰")
                .discountType("FIXED_AMOUNT")
                .discountAmount(1000L)
                .status("ACTIVE")
                .issuedAt(LocalDateTime.now())
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(30))
                .build();

        IssueCouponRequest request = new IssueCouponRequest();
        request.setCouponId(TEST_COUPON_ID);

        when(couponService.issueCoupon(anyLong(), anyLong()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/coupons/issue")
                .header("X-USER-ID", TEST_USER_ID)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").isMap())
                .andExpect(jsonPath("$.user_coupon_id").isNumber())
                .andExpect(jsonPath("$.coupon_name").isString())
                .andExpect(jsonPath("$.discount_type").isString())
                .andExpect(jsonPath("$.status").isString());
    }

    @Test
    @DisplayName("사용자 쿠폰 조회 - 응답 필드 검증")
    void testGetUserCoupons_ResponseFieldValidation() throws Exception {
        // Given
        when(couponService.getUserCoupons(anyLong(), anyString()))
                .thenReturn(Collections.singletonList(
                        UserCouponResponse.builder()
                                .userCouponId(100L)
                                .couponId(1L)
                                .couponName("테스트")
                                .discountType("FIXED_AMOUNT")
                                .discountAmount(1000L)
                                .status("ACTIVE")
                                .issuedAt(LocalDateTime.now())
                                .validFrom(LocalDateTime.now())
                                .validUntil(LocalDateTime.now().plusDays(30))
                                .build()
                ));

        // When & Then
        mockMvc.perform(get("/coupons/issued")
                .header("X-USER-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_coupons").isArray())
                .andExpect(jsonPath("$.user_coupons[0].user_coupon_id").isNumber())
                .andExpect(jsonPath("$.user_coupons[0].coupon_name").isString())
                .andExpect(jsonPath("$.user_coupons[0].status").isString());
    }

    @Test
    @DisplayName("발급 가능한 쿠폰 조회 - 응답 필드 검증")
    void testGetAvailableCoupons_ResponseFieldValidation() throws Exception {
        // Given
        when(couponService.getAvailableCoupons())
                .thenReturn(Collections.singletonList(
                        AvailableCouponResponse.builder()
                                .couponId(1L)
                                .couponName("테스트 쿠폰")
                                .discountType("FIXED_AMOUNT")
                                .discountAmount(1000L)
                                .validFrom(LocalDateTime.now())
                                .validUntil(LocalDateTime.now().plusDays(30))
                                .remainingQty(100)
                                .build()
                ));

        // When & Then
        mockMvc.perform(get("/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons").isArray())
                .andExpect(jsonPath("$.coupons[0].coupon_id").isNumber())
                .andExpect(jsonPath("$.coupons[0].coupon_name").isString())
                .andExpect(jsonPath("$.coupons[0].discount_type").isString());
    }
}
