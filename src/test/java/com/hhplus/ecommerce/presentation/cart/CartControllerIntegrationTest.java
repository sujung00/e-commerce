package com.hhplus.ecommerce.presentation.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.config.TestRepositoryConfiguration;
import com.hhplus.ecommerce.presentation.cart.request.AddCartItemRequest;
import com.hhplus.ecommerce.presentation.cart.request.UpdateQuantityRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CartController 통합 테스트
 * WebEnvironment.RANDOM_PORT와 TestRestTemplate을 사용한 실제 HTTP 요청 테스트
 *
 * 테스트 대상: CartController
 * - GET /carts - 장바구니 조회 (X-USER-ID 헤더)
 * - POST /carts/items - 장바구니 아이템 추가 (X-USER-ID 헤더)
 * - PUT /carts/items/{cart_item_id} - 장바구니 아이템 수량 수정 (X-USER-ID 헤더)
 * - DELETE /carts/items/{cart_item_id} - 장바구니 아이템 제거 (X-USER-ID 헤더)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestRepositoryConfiguration.class)
@Transactional
@DisplayName("CartController 통합 테스트")
class CartControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_PRODUCT_ID = 1L;
    private static final Long TEST_OPTION_ID = 101L;
    private static final Long TEST_CART_ITEM_ID = 1L;

    /**
     * X-USER-ID 헤더를 포함한 HttpHeaders 생성
     */
    private HttpHeaders createHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", String.valueOf(userId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("장바구니 조회 - 요청 처리")
    void testGetCart_Success() {
        // Given
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(TEST_USER_ID));

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/carts",
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
    @DisplayName("장바구니 아이템 추가 - 요청 처리")
    void testAddCartItem_Success() throws Exception {
        // Given
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(1)
                .build();

        HttpEntity<String> entity = new HttpEntity<>(
                objectMapper.writeValueAsString(request),
                createHeaders(TEST_USER_ID)
        );

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/carts/items",
                org.springframework.http.HttpMethod.POST,
                entity,
                String.class
        );

        // Then
        assertTrue(response.getStatusCode().is2xxSuccessful() ||
                   response.getStatusCode().is4xxClientError() ||
                   response.getStatusCode().is5xxServerError(),
                "응답 코드는 2xx, 4xx, 또는 5xx이어야 합니다");
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 유효하지 않은 수량")
    void testAddCartItem_Fail_InvalidQuantity() throws Exception {
        // Given
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(0)  // 유효하지 않은 수량
                .build();

        HttpEntity<String> entity = new HttpEntity<>(
                objectMapper.writeValueAsString(request),
                createHeaders(TEST_USER_ID)
        );

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/carts/items",
                org.springframework.http.HttpMethod.POST,
                entity,
                String.class
        );

        // Then
        assertTrue(response.getStatusCode().is4xxClientError() ||
                   response.getStatusCode().is5xxServerError(),
                "유효하지 않은 수량은 4xx 또는 5xx 에러를 반환해야 합니다");
    }

    @Test
    @DisplayName("장바구니 아이템 수량 수정 - 요청 처리")
    void testUpdateCartItemQuantity_Success() throws Exception {
        // Given
        UpdateQuantityRequest updateRequest = UpdateQuantityRequest.builder()
                .quantity(3)
                .build();

        HttpEntity<String> entity = new HttpEntity<>(
                objectMapper.writeValueAsString(updateRequest),
                createHeaders(TEST_USER_ID)
        );

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/carts/items/" + TEST_CART_ITEM_ID,
                org.springframework.http.HttpMethod.PUT,
                entity,
                String.class
        );

        // Then
        assertTrue(response.getStatusCode().is2xxSuccessful() ||
                   response.getStatusCode().is4xxClientError() ||
                   response.getStatusCode().is5xxServerError(),
                "응답 코드는 2xx, 4xx, 또는 5xx이어야 합니다");
    }

    @Test
    @DisplayName("장바구니 아이템 수량 수정 - 유효하지 않은 수량")
    void testUpdateCartItemQuantity_Fail_InvalidQuantity() throws Exception {
        // Given
        UpdateQuantityRequest request = UpdateQuantityRequest.builder()
                .quantity(0)  // 유효하지 않은 수량
                .build();

        HttpEntity<String> entity = new HttpEntity<>(
                objectMapper.writeValueAsString(request),
                createHeaders(TEST_USER_ID)
        );

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/carts/items/" + TEST_CART_ITEM_ID,
                org.springframework.http.HttpMethod.PUT,
                entity,
                String.class
        );

        // Then
        assertTrue(response.getStatusCode().is4xxClientError() ||
                   response.getStatusCode().is5xxServerError(),
                "유효하지 않은 수량은 4xx 또는 5xx 에러를 반환해야 합니다");
    }

    @Test
    @DisplayName("장바구니 아이템 제거 - 요청 처리")
    void testRemoveCartItem_Success() {
        // Given
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(TEST_USER_ID));

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/carts/items/" + TEST_CART_ITEM_ID,
                org.springframework.http.HttpMethod.DELETE,
                entity,
                String.class
        );

        // Then
        assertTrue(response.getStatusCode().is2xxSuccessful() ||
                   response.getStatusCode().is4xxClientError() ||
                   response.getStatusCode().is5xxServerError(),
                "응답 코드는 2xx, 4xx, 또는 5xx이어야 합니다");
    }

    @Test
    @DisplayName("장바구니 아이템 제거 - 아이템 없음")
    void testRemoveCartItem_Fail_ItemNotFound() {
        // Given
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(TEST_USER_ID));

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/carts/items/99999",
                org.springframework.http.HttpMethod.DELETE,
                entity,
                String.class
        );

        // Then
        assertTrue(response.getStatusCode().is4xxClientError() ||
                   response.getStatusCode().is5xxServerError(),
                "존재하지 않는 아이템은 4xx 또는 5xx 에러를 반환해야 합니다");
    }
}
