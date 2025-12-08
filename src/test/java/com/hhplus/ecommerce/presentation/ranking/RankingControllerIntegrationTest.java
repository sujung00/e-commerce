package com.hhplus.ecommerce.presentation.ranking;

import com.hhplus.ecommerce.integration.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RankingController 통합 테스트
 * 실제 Redis를 사용하여 WebEnvironment.RANDOM_PORT와 TestRestTemplate을 통한 HTTP 요청 테스트
 * BaseIntegrationTest를 상속받아 TestContainers 자동 관리
 *
 * 테스트 대상: RankingController
 * - GET /ranking/top/{topN} - TOP N 상품 조회
 * - GET /ranking/{productId} - 특정 상품 순위 조회
 */
@DisplayName("RankingController 통합 테스트")
public class RankingControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // ========== TOP N 상품 조회 (GET /ranking/top/{topN}) ==========

    @Test
    @DisplayName("TOP N 상품 조회 - 성공")
    void testGetTopProducts_Success() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/ranking/top/5",
                String.class
        );

        // Then
        assertNotNull(response.getStatusCode());
        assertTrue(response.getStatusCode().is2xxSuccessful(),
                "상위 5개 상품 조회는 성공해야 합니다");
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("top_products"),
                "응답에 top_products 필드가 있어야 합니다");
    }

    @Test
    @DisplayName("TOP N 상품 조회 - 유효하지 않은 topN (0)")
    void testGetTopProducts_InvalidTopN_Zero() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/ranking/top/0",
                String.class
        );

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "topN=0은 400 Bad Request를 반환해야 합니다");
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("message"),
                "에러 메시지가 포함되어야 합니다");
    }

    @Test
    @DisplayName("TOP N 상품 조회 - 유효하지 않은 topN (음수)")
    void testGetTopProducts_InvalidTopN_Negative() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/ranking/top/-5",
                String.class
        );

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "음수 topN은 400 Bad Request를 반환해야 합니다");
    }

    @Test
    @DisplayName("TOP N 상품 조회 - 다양한 topN 값")
    void testGetTopProducts_VariousTopNValues() {
        // Test topN=1
        ResponseEntity<String> response1 = restTemplate.getForEntity(
                "/ranking/top/1",
                String.class
        );
        assertTrue(response1.getStatusCode().is2xxSuccessful());

        // Test topN=10
        ResponseEntity<String> response10 = restTemplate.getForEntity(
                "/ranking/top/10",
                String.class
        );
        assertTrue(response10.getStatusCode().is2xxSuccessful());

        // Test topN=100
        ResponseEntity<String> response100 = restTemplate.getForEntity(
                "/ranking/top/100",
                String.class
        );
        assertTrue(response100.getStatusCode().is2xxSuccessful());
    }

    // ========== 특정 상품 순위 조회 (GET /ranking/{productId}) ==========

    @Test
    @DisplayName("상품 순위 조회 - 성공")
    void testGetProductRank_Success() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/ranking/100",
                String.class
        );

        // Then
        assertNotNull(response.getStatusCode());
        assertTrue(response.getStatusCode().is2xxSuccessful(),
                "상품 순위 조회는 성공해야 합니다");
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("product_id"),
                "응답에 product_id 필드가 있어야 합니다");
    }

    @Test
    @DisplayName("상품 순위 조회 - 유효하지 않은 productId (0)")
    void testGetProductRank_InvalidProductId_Zero() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/ranking/0",
                String.class
        );

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "productId=0은 400 Bad Request를 반환해야 합니다");
    }

    @Test
    @DisplayName("상품 순위 조회 - 유효하지 않은 productId (음수)")
    void testGetProductRank_InvalidProductId_Negative() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/ranking/-100",
                String.class
        );

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "음수 productId는 400 Bad Request를 반환해야 합니다");
    }

    @Test
    @DisplayName("상품 순위 조회 - 다양한 productId 값")
    void testGetProductRank_VariousProductIds() {
        // Test productId=1
        ResponseEntity<String> response1 = restTemplate.getForEntity(
                "/ranking/1",
                String.class
        );
        assertTrue(response1.getStatusCode().is2xxSuccessful());

        // Test productId=100
        ResponseEntity<String> response100 = restTemplate.getForEntity(
                "/ranking/100",
                String.class
        );
        assertTrue(response100.getStatusCode().is2xxSuccessful());

        // Test large productId
        ResponseEntity<String> responseMax = restTemplate.getForEntity(
                "/ranking/999999999",
                String.class
        );
        assertTrue(responseMax.getStatusCode().is2xxSuccessful());
    }

    @Test
    @DisplayName("상품 순위 조회 - 응답 구조 검증")
    void testGetProductRank_ResponseStructure() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/ranking/100",
                String.class
        );

        // Then
        assertTrue(response.getStatusCode().is2xxSuccessful());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("product_id"), "product_id 필드 필수");
        assertTrue(body.contains("rank"), "rank 필드 필수");
        assertTrue(body.contains("score"), "score 필드 필수");
    }

    @Test
    @DisplayName("TOP N 상품 조회 - 응답 구조 검증")
    void testGetTopProducts_ResponseStructure() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/ranking/top/5",
                String.class
        );

        // Then
        assertTrue(response.getStatusCode().is2xxSuccessful());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("top_products"), "top_products 필드 필수");
    }
}
