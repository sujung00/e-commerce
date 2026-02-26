package com.hhplus.ecommerce.presentation.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.application.coupon.CouponService;
import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.event.CouponIssueRequest;
import com.hhplus.ecommerce.infrastructure.kafka.CouponIssueProducer;
import com.hhplus.ecommerce.presentation.coupon.request.IssueCouponRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CouponKafkaFailureTest - Kafka 발행 실패 시나리오 테스트
 *
 * 테스트 목적:
 * - Kafka 발행 실패 시 HTTP 500 응답 확인
 * - 타임아웃 발생 시 에러 메시지 확인
 * - 네트워크 오류 시 GlobalExceptionHandler 처리 확인
 * - 성공/실패 케이스별 HTTP 상태 코드 구분 확인
 *
 * 테스트 방법:
 * - @MockitoBean으로 KafkaTemplate Mock 생성 (Spring Boot 3.4+ 권장 방식)
 * - CompletableFuture.failedFuture()로 실패 시뮬레이션
 * - MockMvc로 HTTP 요청/응답 검증
 *
 * 검증 항목:
 * 1. Kafka 타임아웃 → HTTP 500
 * 2. Kafka 네트워크 오류 → HTTP 500
 * 3. 에러 코드: KAFKA_PUBLISH_FAILED
 * 4. 에러 메시지에 상세 정보 포함
 *
 * 변경 이력 (Spring Boot 3.4+ Bean Override):
 * - @MockBean (deprecated in Spring Boot 3.4.0+) → @MockitoBean (신규)
 * - 기존: org.springframework.boot.test.mock.mockito.MockBean (deprecated)
 * - 신규: org.springframework.test.context.bean.override.mockito.MockitoBean
 * - Spring Framework 6.2+ Bean Override 메커니즘 사용
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Kafka 발행 실패 시나리오 테스트")
class CouponKafkaFailureTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * KafkaTemplate Mock 객체
     *
     * Spring Boot 3.4+ Bean Override 메커니즘:
     * - @MockBean (deprecated) → @MockitoBean (신규)
     * - 기존: org.springframework.boot.test.mock.mockito.MockBean (deprecated in 3.4.0+)
     * - 신규: org.springframework.test.context.bean.override.mockito.MockitoBean
     * - Spring Framework 6.2+의 새로운 Bean Override 테스트 지원
     *
     * 역할:
     * - Kafka 발행 실패 시나리오를 시뮬레이션하기 위해 KafkaTemplate을 Mock으로 대체
     * - CompletableFuture.failedFuture()를 반환하여 타임아웃, 네트워크 오류 등을 재현
     */
    @MockitoBean
    private KafkaTemplate<String, CouponIssueRequest> kafkaTemplate;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // 캐시 초기화
        if (cacheManager != null) {
            cacheManager.getCacheNames().forEach(cacheName ->
                    cacheManager.getCache(cacheName).clear());
        }

        // 테스트용 쿠폰 생성
        createTestCoupon();
    }

    /**
     * 테스트 1: Kafka 타임아웃 발생 시 HTTP 500 응답
     *
     * 시나리오:
     * 1. KafkaTemplate.send()가 타임아웃으로 실패하도록 Mock
     * 2. POST /coupons/issue/kafka 요청
     * 3. HTTP 500 응답 확인
     * 4. 에러 코드: KAFKA_PUBLISH_FAILED
     * 5. 에러 메시지에 "타임아웃" 포함
     */
    @Test
    @DisplayName("Kafka 타임아웃 발생 시 HTTP 500 응답")
    void testKafkaTimeout() throws Exception {
        // Given - Kafka 타임아웃 시뮬레이션
        CompletableFuture<SendResult<String, CouponIssueRequest>> failedFuture =
                CompletableFuture.failedFuture(
                        new TimeoutException("Kafka broker did not respond within 5 seconds")
                );
        when(kafkaTemplate.send(anyString(), anyString(), any(CouponIssueRequest.class)))
                .thenReturn(failedFuture);

        // When - POST /coupons/issue/kafka
        IssueCouponRequest request = new IssueCouponRequest(1L);

        // Then - HTTP 500 응답 확인
        mockMvc.perform(post("/coupons/issue/kafka")
                        .header("X-USER-ID", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isInternalServerError())  // HTTP 500
                .andExpect(jsonPath("$.error_code").value("KAFKA_PUBLISH_FAILED"))
                .andExpect(jsonPath("$.error_message").value(containsString("Kafka")))
                .andExpect(jsonPath("$.error_message").value(containsString("타임아웃")));
    }

    /**
     * 테스트 2: Kafka 네트워크 오류 발생 시 HTTP 500 응답
     *
     * 시나리오:
     * 1. KafkaTemplate.send()가 네트워크 오류로 실패하도록 Mock
     * 2. POST /coupons/issue/kafka 요청
     * 3. HTTP 500 응답 확인
     * 4. 에러 코드: KAFKA_PUBLISH_FAILED
     * 5. 에러 메시지에 "실패" 포함
     */
    @Test
    @DisplayName("Kafka 네트워크 오류 발생 시 HTTP 500 응답")
    void testKafkaNetworkError() throws Exception {
        // Given - Kafka 네트워크 오류 시뮬레이션
        CompletableFuture<SendResult<String, CouponIssueRequest>> failedFuture =
                CompletableFuture.failedFuture(
                        new RuntimeException("Failed to send message to Kafka broker: Connection refused")
                );
        when(kafkaTemplate.send(anyString(), anyString(), any(CouponIssueRequest.class)))
                .thenReturn(failedFuture);

        // When - POST /coupons/issue/kafka
        IssueCouponRequest request = new IssueCouponRequest(1L);

        // Then - HTTP 500 응답 확인
        mockMvc.perform(post("/coupons/issue/kafka")
                        .header("X-USER-ID", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isInternalServerError())  // HTTP 500
                .andExpect(jsonPath("$.error_code").value("KAFKA_PUBLISH_FAILED"))
                .andExpect(jsonPath("$.error_message").value(containsString("Kafka")))
                .andExpect(jsonPath("$.error_message").value(containsString("실패")));
    }

    /**
     * 테스트 3: Kafka 브로커 다운 시 HTTP 500 응답
     *
     * 시나리오:
     * 1. KafkaTemplate.send()가 브로커 다운 오류로 실패하도록 Mock
     * 2. POST /coupons/issue/kafka 요청
     * 3. HTTP 500 응답 확인
     * 4. 에러 코드: KAFKA_PUBLISH_FAILED
     */
    @Test
    @DisplayName("Kafka 브로커 다운 시 HTTP 500 응답")
    void testKafkaBrokerDown() throws Exception {
        // Given - Kafka 브로커 다운 시뮬레이션
        CompletableFuture<SendResult<String, CouponIssueRequest>> failedFuture =
                CompletableFuture.failedFuture(
                        new RuntimeException("Kafka broker is not available")
                );
        when(kafkaTemplate.send(anyString(), anyString(), any(CouponIssueRequest.class)))
                .thenReturn(failedFuture);

        // When - POST /coupons/issue/kafka
        IssueCouponRequest request = new IssueCouponRequest(1L);

        // Then - HTTP 500 응답 확인
        mockMvc.perform(post("/coupons/issue/kafka")
                        .header("X-USER-ID", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isInternalServerError())  // HTTP 500
                .andExpect(jsonPath("$.error_code").value("KAFKA_PUBLISH_FAILED"));
    }

    /**
     * 테스트 4: 성공 케이스 - HTTP 202 Accepted 응답
     *
     * 시나리오:
     * 1. KafkaTemplate.send()가 성공하도록 Mock
     * 2. POST /coupons/issue/kafka 요청
     * 3. HTTP 202 Accepted 응답 확인
     * 4. requestId 반환 확인
     */
    @Test
    @DisplayName("Kafka 발행 성공 시 HTTP 202 Accepted 응답")
    void testKafkaPublishSuccess() throws Exception {
        // Given - Kafka 발행 성공 시뮬레이션
        SendResult<String, CouponIssueRequest> sendResult =
                mockSuccessfulSendResult();
        CompletableFuture<SendResult<String, CouponIssueRequest>> successFuture =
                CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), any(CouponIssueRequest.class)))
                .thenReturn(successFuture);

        // When - POST /coupons/issue/kafka
        IssueCouponRequest request = new IssueCouponRequest(1L);

        // Then - HTTP 202 응답 확인
        mockMvc.perform(post("/coupons/issue/kafka")
                        .header("X-USER-ID", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isAccepted())  // HTTP 202
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value(containsString("Kafka")));
    }

    /**
     * 테스트 5: 유효하지 않은 쿠폰 ID → HTTP 400 Bad Request
     *
     * 시나리오:
     * 1. 캐시에 존재하지 않는 쿠폰 ID로 요청
     * 2. POST /coupons/issue/kafka 요청
     * 3. HTTP 400 응답 확인 (Kafka 발행 전에 실패)
     */
    @Test
    @DisplayName("유효하지 않은 쿠폰 ID 시 HTTP 400 Bad Request")
    void testInvalidCouponId() throws Exception {
        // Given - 존재하지 않는 쿠폰 ID
        IssueCouponRequest request = new IssueCouponRequest(999L);

        // When & Then - HTTP 400 응답 확인
        mockMvc.perform(post("/coupons/issue/kafka")
                        .header("X-USER-ID", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())  // HTTP 400
                .andExpect(jsonPath("$.error_code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error_message").value(containsString("쿠폰")));
    }

    // ===== Helper Methods =====

    /**
     * 테스트용 쿠폰 생성
     */
    private void createTestCoupon() {
        if (couponRepository.findById(1L).isEmpty()) {
            Coupon coupon = Coupon.builder()
                    .couponId(1L)
                    .couponName("Test Coupon")
                    .discountType("FIXED_AMOUNT")
                    .discountAmount(10000L)
                    .totalQuantity(100)
                    .remainingQty(100)
                    .isActive(true)
                    .validFrom(LocalDateTime.now().minusDays(1))
                    .validUntil(LocalDateTime.now().plusDays(30))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            couponRepository.save(coupon);

            // 캐시에도 추가 (getAvailableCoupons 호출)
            // CouponService를 통해 캐시 초기화할 수 있지만, 여기서는 단순화
        }
    }

    /**
     * Mock SendResult 생성 (성공 케이스용)
     */
    @SuppressWarnings("unchecked")
    private SendResult<String, CouponIssueRequest> mockSuccessfulSendResult() {
        // SendResult를 직접 생성하는 대신, Mock 객체 사용
        // 실제로는 RecordMetadata를 포함해야 하지만, 테스트에서는 단순화
        return (SendResult<String, CouponIssueRequest>) org.mockito.Mockito.mock(SendResult.class);
    }
}
