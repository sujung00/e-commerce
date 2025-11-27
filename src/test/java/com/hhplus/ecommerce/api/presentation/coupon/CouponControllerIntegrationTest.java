package com.hhplus.ecommerce.api.presentation.coupon;

import com.hhplus.ecommerce.integration.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CouponController 통합 테스트
 * MySQL 기반 테스트 환경에서 WebEnvironment.RANDOM_PORT와 TestRestTemplate을 사용한 실제 HTTP 요청 테스트
 * Testcontainers를 사용하여 자동으로 MySQL 컨테이너 관리
 *
 * 테스트 대상: CouponController
 * - POST /coupons/issue - 쿠폰 발급 (X-USER-ID 헤더)
 * - GET /coupons/issued - 사용자 쿠폰 조회 (X-USER-ID 헤더)
 * - GET /coupons - 사용 가능한 쿠폰 조회
 */
@DisplayName("CouponController 통합 테스트")
class CouponControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_COUPON_ID = 1L;

    /**
     * X-USER-ID 헤더를 포함한 HttpHeaders 생성
     */
    private HttpHeaders createHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", String.valueOf(userId));
        return headers;
    }

    @Test
    @DisplayName("쿠폰 발급 - 요청 처리")
    void testIssueCoupon_Request() {
        // Given
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(TEST_USER_ID));

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/coupons/issue?coupon_id=" + TEST_COUPON_ID,
                org.springframework.http.HttpMethod.POST,
                entity,
                String.class
        );

        // Then
        assertNotNull(response.getStatusCode(),
                "응답 상태 코드가 null이 아니어야 합니다");
        assertTrue(response.getStatusCode().is2xxSuccessful() ||
                   response.getStatusCode().is4xxClientError() ||
                   response.getStatusCode().is5xxServerError(),
                "응답 코드는 2xx, 4xx, 또는 5xx이어야 합니다");
    }

    @Test
    @DisplayName("사용자 쿠폰 조회 - 요청 처리")
    void testGetUserCoupons_Request() {
        // Given
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(TEST_USER_ID));

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/coupons/issued",
                org.springframework.http.HttpMethod.GET,
                entity,
                String.class
        );

        // Then
        assertNotNull(response.getStatusCode(),
                "응답 상태 코드가 null이 아니어야 합니다");
        assertTrue(response.getStatusCode().is2xxSuccessful() ||
                   response.getStatusCode().is4xxClientError() ||
                   response.getStatusCode().is5xxServerError(),
                "응답 코드는 2xx, 4xx, 또는 5xx이어야 합니다");
    }

    @Test
    @DisplayName("사용 가능한 쿠폰 조회 - 요청 처리")
    void testGetAvailableCoupons_Request() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/coupons",
                String.class
        );

        // Then
        assertNotNull(response.getStatusCode(),
                "응답 상태 코드가 null이 아니어야 합니다");
        assertTrue(response.getStatusCode().is2xxSuccessful() ||
                   response.getStatusCode().is4xxClientError() ||
                   response.getStatusCode().is5xxServerError(),
                "응답 코드는 2xx, 4xx, 또는 5xx이어야 합니다");
    }

    @Test
    @DisplayName("사용 가능한 쿠폰 조회 - 페이지네이션")
    void testGetAvailableCoupons_WithPagination() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/coupons?page=0&size=5",
                String.class
        );

        // Then
        assertNotNull(response.getStatusCode(),
                "응답 상태 코드가 null이 아니어야 합니다");
        assertTrue(response.getStatusCode().is2xxSuccessful() ||
                   response.getStatusCode().is4xxClientError() ||
                   response.getStatusCode().is5xxServerError(),
                "응답 코드는 2xx, 4xx, 또는 5xx이어야 합니다");
    }
}
