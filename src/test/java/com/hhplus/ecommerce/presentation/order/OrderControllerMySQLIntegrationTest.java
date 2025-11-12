package com.hhplus.ecommerce.presentation.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.config.TestRepositoryConfiguration;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.presentation.order.request.CreateOrderRequest;
import com.hhplus.ecommerce.presentation.order.request.OrderItemRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderController MySQL 통합 테스트 - 완전한 예시
 * 실제 MySQL 데이터베이스를 사용하는 통합 테스트
 * - 테스트별 데이터 격리 (@Transactional)
 * - 테스트 데이터 생성 (@BeforeEach)
 * - 자동 롤백 (테스트 후)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestRepositoryConfiguration.class)
@Transactional
@DisplayName("OrderController MySQL 통합 테스트")
class OrderControllerMySQLIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CouponRepository couponRepository;

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_PRODUCT_ID = 1L;
    private static final Long TEST_OPTION_ID = 101L;
    private static final Long TEST_COUPON_ID = 1L;

    private User testUser;
    private Product testProduct;
    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        createTestData();
    }

    private void createTestData() {
        // 사용자 생성
        testUser = User.builder()
                .userId(TEST_USER_ID)
                .email("order.test@example.com")
                .name("주문 테스트 사용자")
                .balance(500000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(testUser);

        // 상품 옵션 생성
        ProductOption option = ProductOption.builder()
                .optionId(TEST_OPTION_ID)
                .productId(TEST_PRODUCT_ID)
                .name("기본 옵션")
                .stock(100)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 상품 생성
        testProduct = Product.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("테스트 상품")
                .description("주문 테스트용 상품")
                .price(50000L)
                .totalStock(100)
                .status("IN_STOCK")
                .options(new ArrayList<>(List.of(option)))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.save(testProduct);
        productRepository.saveOption(option);

        // 쿠폰 생성
        testCoupon = Coupon.builder()
                .couponId(TEST_COUPON_ID)
                .couponName("테스트 할인 쿠폰")
                .description("5000원 할인")
                .discountType("FIXED_AMOUNT")
                .discountAmount(5000L)
                .isActive(true)
                .totalQuantity(100)
                .remainingQty(100)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        couponRepository.save(testCoupon);
    }

    private HttpHeaders createHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", String.valueOf(userId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("주문 생성 - 성공 (쿠폰 미적용)")
    void testCreateOrder_Success_NoCoupon() {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderItems(List.of(
                        OrderItemRequest.builder()
                                .productId(TEST_PRODUCT_ID)
                                .optionId(TEST_OPTION_ID)
                                .quantity(2)
                                .build()
                ))
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
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"order_id\""));
        assertTrue(response.getBody().contains("\"final_amount\":100000"));
    }

    @Test
    @DisplayName("주문 생성 - 성공 (쿠폰 적용)")
    void testCreateOrder_Success_WithCoupon() {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderItems(List.of(
                        OrderItemRequest.builder()
                                .productId(TEST_PRODUCT_ID)
                                .optionId(TEST_OPTION_ID)
                                .quantity(2)
                                .build()
                ))
                .couponId(TEST_COUPON_ID)
                .build();

        HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, createHeaders(TEST_USER_ID));

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/orders",
                entity,
                String.class
        );

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"coupon_discount\":5000"));
        assertTrue(response.getBody().contains("\"final_amount\":95000"));
    }

    @Test
    @DisplayName("주문 생성 - 실패 (X-USER-ID 헤더 누락)")
    void testCreateOrder_Failed_MissingHeader() {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderItems(List.of(
                        OrderItemRequest.builder()
                                .productId(TEST_PRODUCT_ID)
                                .optionId(TEST_OPTION_ID)
                                .quantity(1)
                                .build()
                ))
                .couponId(null)
                .build();

        HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, new HttpHeaders());
        entity.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/orders",
                entity,
                String.class
        );

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("주문 목록 조회 - 성공")
    void testGetOrderList_Success() {
        // Given - 주문 생성
        CreateOrderRequest createRequest = CreateOrderRequest.builder()
                .orderItems(List.of(
                        OrderItemRequest.builder()
                                .productId(TEST_PRODUCT_ID)
                                .optionId(TEST_OPTION_ID)
                                .quantity(1)
                                .build()
                ))
                .couponId(null)
                .build();

        HttpEntity<CreateOrderRequest> createEntity = new HttpEntity<>(
                createRequest,
                createHeaders(TEST_USER_ID)
        );

        restTemplate.postForEntity("/orders", createEntity, String.class);

        // When - 주문 목록 조회
        ResponseEntity<String> response = restTemplate.exchange(
                "/orders?page=0&size=10",
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(createHeaders(TEST_USER_ID)),
                String.class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"orders\""));
    }

    @Test
    @DisplayName("주문 상세 조회 - 성공")
    void testGetOrderDetail_Success() {
        // Given - 주문 생성
        CreateOrderRequest createRequest = CreateOrderRequest.builder()
                .orderItems(List.of(
                        OrderItemRequest.builder()
                                .productId(TEST_PRODUCT_ID)
                                .optionId(TEST_OPTION_ID)
                                .quantity(1)
                                .build()
                ))
                .couponId(null)
                .build();

        HttpEntity<CreateOrderRequest> createEntity = new HttpEntity<>(
                createRequest,
                createHeaders(TEST_USER_ID)
        );

        ResponseEntity<String> createResponse = restTemplate.postForEntity(
                "/orders",
                createEntity,
                String.class
        );

        assertTrue(createResponse.getStatusCode() == HttpStatus.CREATED);
        String responseBody = createResponse.getBody();
        long orderId = extractOrderId(responseBody);

        // When - 주문 상세 조회
        ResponseEntity<String> response = restTemplate.exchange(
                "/orders/" + orderId,
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(createHeaders(TEST_USER_ID)),
                String.class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"order_id\":" + orderId));
    }

    @Test
    @DisplayName("주문 생성 - 실패 (잔액 부족)")
    void testCreateOrder_Failed_InsufficientBalance() {
        // Given - 잔액 부족한 사용자
        User poorUser = User.builder()
                .userId(9999L)
                .email("poor@example.com")
                .name("빈곤한 사용자")
                .balance(1000L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(poorUser);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderItems(List.of(
                        OrderItemRequest.builder()
                                .productId(TEST_PRODUCT_ID)
                                .optionId(TEST_OPTION_ID)
                                .quantity(2)
                                .build()
                ))
                .couponId(null)
                .build();

        HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, createHeaders(9999L));

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/orders",
                entity,
                String.class
        );

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    private long extractOrderId(String jsonResponse) {
        try {
            int startIndex = jsonResponse.indexOf("\"order_id\":") + 11;
            int endIndex = jsonResponse.indexOf(",", startIndex);
            String orderId = jsonResponse.substring(startIndex, endIndex).trim();
            return Long.parseLong(orderId);
        } catch (Exception e) {
            fail("주문 ID 추출 실패: " + e.getMessage());
            return -1;
        }
    }
}
