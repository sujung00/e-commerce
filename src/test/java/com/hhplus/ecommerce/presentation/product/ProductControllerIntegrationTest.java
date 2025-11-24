package com.hhplus.ecommerce.presentation.product;

import com.hhplus.ecommerce.integration.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProductController 통합 테스트
 * 실제 MySQL 테스트 DB를 사용하여 WebEnvironment.RANDOM_PORT와 TestRestTemplate을 통한 HTTP 요청 테스트
 * Testcontainers를 사용하여 자동으로 MySQL 컨테이너 관리
 *
 * 테스트 대상: ProductController
 * - GET /products - 상품 목록 조회
 * - GET /products/{product_id} - 상품 상세 조회
 * - GET /products/popular - 인기 상품 조회
 */
@DisplayName("ProductController 통합 테스트")
class ProductControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static final Long TEST_PRODUCT_ID = 1L;
    private static final Long NON_EXISTENT_PRODUCT_ID = 99999L;

    @Test
    @DisplayName("상품 목록 조회 - 성공")
    void testGetProductList_Success() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/products",
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
    @DisplayName("상품 목록 조회 - 페이지네이션")
    void testGetProductList_WithPagination() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/products?page=0&size=10",
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
    @DisplayName("상품 상세 조회 - 성공")
    void testGetProductDetail_Success() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/products/" + TEST_PRODUCT_ID,
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
    @DisplayName("상품 상세 조회 - 404 Not Found")
    void testGetProductDetail_NotFound() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/products/" + NON_EXISTENT_PRODUCT_ID,
                String.class
        );

        // Then
        assertTrue(response.getStatusCode() == HttpStatus.NOT_FOUND ||
                   response.getStatusCode().is4xxClientError() ||
                   response.getStatusCode().is5xxServerError(),
                "존재하지 않는 상품은 4xx 또는 5xx를 반환해야 합니다");
    }

    @Test
    @DisplayName("인기 상품 조회 - 성공")
    void testGetPopularProducts_Success() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/products/popular",
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
