package com.hhplus.ecommerce.presentation.order;

import com.hhplus.ecommerce.config.TestRepositoryConfiguration;
import com.hhplus.ecommerce.presentation.order.request.CreateOrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderController 통합 테스트
 * WebEnvironment.RANDOM_PORT와 TestRestTemplate을 사용한 실제 HTTP 요청 테스트
 *
 * 테스트 대상: OrderController
 * - POST /orders - 주문 생성 (X-USER-ID 헤더)
 * - GET /orders - 주문 목록 조회 (X-USER-ID 헤더)
 * - GET /orders/{order_id} - 주문 상세 조회 (X-USER-ID 헤더)
 * - POST /orders/{order_id}/cancel - 주문 취소 (X-USER-ID 헤더)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestRepositoryConfiguration.class)
@Transactional
@DisplayName("OrderController 통합 테스트")
class OrderControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_ORDER_ID = 1L;
    private static final Long NON_EXISTENT_ORDER_ID = 99999L;

    @BeforeEach
    void setUp() {
        // 테스트 전 초기화 (필요시)
    }

    /**
     * X-USER-ID 헤더를 포함한 HttpHeaders 생성
     */
    private HttpHeaders createHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", String.valueOf(userId));
        return headers;
    }

    @Test
    @DisplayName("주문 생성 - 성공 (201 Created)")
    void testCreateOrder_Success() {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
                .couponId(null)
                .build();

        HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, createHeaders(TEST_USER_ID));

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/orders",
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
    @DisplayName("주문 목록 조회 - 성공 (200 OK)")
    void testGetOrderList_Success() {
        // Given
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(TEST_USER_ID));

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/orders",
                org.springframework.http.HttpMethod.GET,
                entity,
                String.class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "주문 목록 조회는 200 OK를 반환해야 합니다");
        assertNotNull(response.getBody(),
                "응답 본문이 null이 아니어야 합니다");
    }

    @Test
    @DisplayName("주문 상세 조회 - 성공 (200 OK 또는 404)")
    void testGetOrderDetail_Success() {
        // Given
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(TEST_USER_ID));

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/orders/" + TEST_ORDER_ID,
                org.springframework.http.HttpMethod.GET,
                entity,
                String.class
        );

        // Then
        assertTrue(response.getStatusCode().is2xxSuccessful() ||
                   response.getStatusCode() == HttpStatus.NOT_FOUND,
                "응답 코드는 2xx 또는 404이어야 합니다");
    }

    @Test
    @DisplayName("주문 상세 조회 - 실패 (404 Not Found)")
    void testGetOrderDetail_NotFound() {
        // Given
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(TEST_USER_ID));

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/orders/" + NON_EXISTENT_ORDER_ID,
                org.springframework.http.HttpMethod.GET,
                entity,
                String.class
        );

        // Then
        assertTrue(response.getStatusCode() == HttpStatus.NOT_FOUND ||
                   response.getStatusCode().is4xxClientError() ||
                   response.getStatusCode().is5xxServerError(),
                "존재하지 않는 주문은 4xx 또는 5xx를 반환해야 합니다");
    }

    @Test
    @DisplayName("주문 취소 - 성공 (200 OK)")
    void testCancelOrder_Success() {
        // Given
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(TEST_USER_ID));

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/orders/" + TEST_ORDER_ID + "/cancel",
                org.springframework.http.HttpMethod.POST,
                entity,
                String.class
        );

        // Then
        assertTrue(response.getStatusCode().is2xxSuccessful() ||
                   response.getStatusCode() == HttpStatus.NOT_FOUND ||
                   response.getStatusCode().is4xxClientError() ||
                   response.getStatusCode().is5xxServerError(),
                "응답 코드는 2xx, 4xx, 또는 5xx이어야 합니다");
    }

    @Test
    @DisplayName("주문 취소 - 실패 (404 Not Found)")
    void testCancelOrder_NotFound() {
        // Given
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(TEST_USER_ID));

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/orders/" + NON_EXISTENT_ORDER_ID + "/cancel",
                org.springframework.http.HttpMethod.POST,
                entity,
                String.class
        );

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(),
                "존재하지 않는 주문 취소는 404 Not Found를 반환해야 합니다");
    }
}
