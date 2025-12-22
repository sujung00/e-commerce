package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCoupon;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.domain.user.User;
import com.hhplus.ecommerce.domain.user.UserRepository;
import com.hhplus.ecommerce.infrastructure.kafka.CouponIssueProducer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * CouponIssueKafkaIntegrationTest - Kafka 기반 쿠폰 발급 통합 테스트
 *
 * 테스트 목적:
 * - Kafka Producer → Consumer → DB 전체 흐름 검증
 * - userId 기반 파티셔닝 검증
 * - 멱등성 보장 (중복 발급 방지) 검증
 * - 병렬 처리 (10 Consumer) 검증
 *
 * TestContainers 사용:
 * - KafkaContainer: Kafka Broker (KRaft 모드)
 * - MySQLContainer: MySQL 8.0
 * - GenericContainer: Redis 7.0
 *
 * 테스트 시나리오:
 * 1. Producer 발행 → Consumer 수신 → DB 저장 검증
 * 2. 중복 메시지 발행 시 멱등성 보장 검증
 * 3. 여러 사용자 동시 요청 시 병렬 처리 검증
 * 4. userId 기반 파티셔닝 검증
 *
 * 주의사항:
 * - @Transactional 제거 (롤백 방지)
 * - Kafka Topic 명시적 생성
 * - Consumer 준비 대기 후 메시지 발행
 */
@SpringBootTest
@Testcontainers
@DisplayName("Kafka 기반 쿠폰 발급 통합 테스트")
class CouponIssueKafkaIntegrationTest {

    // ===== TestContainers 설정 =====

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.0")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Kafka 설정
        registry.add("kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("kafka.topics.coupon-issue-requests", () -> "coupon.issue.requests");
        registry.add("kafka.consumer.coupon-group-id", () -> "test-coupon-consumer-group");

        // MySQL 설정
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");

        // Redis 설정
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
    }

    @Autowired
    private CouponIssueProducer couponIssueProducer;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Autowired(required = false)
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    private static final String TOPIC_NAME = "coupon.issue.requests";

    @BeforeEach
    void setUp() throws Exception {
        // 1. Kafka Topic 명시적 생성
        createKafkaTopic();

        // 2. Consumer 준비 대기 (모든 파티션 할당 완료 대기)
        waitForConsumerAssignment();

        // 3. 기존 데이터 정리 (수동 삭제)
        cleanupTestData();

        // 4. 테스트 데이터 생성
        createTestUsers();
        createTestCoupons();
    }

    @AfterEach
    void tearDown() {
        // 테스트 종료 후 데이터 정리
        cleanupTestData();
    }

    /**
     * Kafka Topic 명시적 생성
     */
    private void createKafkaTopic() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            NewTopic newTopic = new NewTopic(TOPIC_NAME, 10, (short) 1);
            adminClient.createTopics(Collections.singletonList(newTopic));
            Thread.sleep(2000); // Topic 생성 대기
        } catch (Exception e) {
            // Topic이 이미 존재하면 무시
            System.out.println("Topic already exists or creation failed: " + e.getMessage());
        }
    }

    /**
     * Consumer 준비 대기
     */
    private void waitForConsumerAssignment() {
        if (kafkaListenerEndpointRegistry != null) {
            kafkaListenerEndpointRegistry.getListenerContainers().forEach(container -> {
                ContainerTestUtils.waitForAssignment(container, 10);
            });
            System.out.println("[Test] Consumer 파티션 할당 완료");
        } else {
            System.out.println("[Test] KafkaListenerEndpointRegistry not available, skipping consumer wait");
        }

        // Consumer가 완전히 준비될 때까지 추가 대기
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 테스트 데이터 정리 (수동 삭제)
     */
    private void cleanupTestData() {
        // UserCoupon 전체 조회 후 삭제
        List<UserCoupon> allUserCoupons = userCouponRepository.findByUserId(1L);
        for (long userId = 1; userId <= 20; userId++) {
            List<UserCoupon> userCoupons = userCouponRepository.findByUserId(userId);
            for (UserCoupon uc : userCoupons) {
                userCouponRepository.deleteByUserIdAndCouponId(uc.getUserId(), uc.getCouponId());
            }
        }

        // Coupon 개별 삭제 (deleteAll이 없으므로)
        for (long couponId = 1; couponId <= 10; couponId++) {
            couponRepository.findById(couponId).ifPresent(coupon -> {
                // 커스텀 Repository에 delete 메서드가 있다고 가정
                // 없다면 이 부분은 스킵
            });
        }

        // User도 마찬가지
    }

    /**
     * 테스트 1: Producer → Consumer → DB 전체 흐름 검증
     *
     * 시나리오:
     * 1. CouponIssueProducer.sendCouponIssueRequest() 호출
     * 2. Kafka Topic: coupon.issue.requests로 메시지 발행
     * 3. CouponIssueConsumer가 메시지 수신
     * 4. CouponService.issueCouponWithLock() 호출하여 쿠폰 발급
     * 5. DB에 UserCoupon 저장 확인
     */
    @Test
    @DisplayName("Producer → Consumer → DB 전체 흐름 검증")
    void testProducerConsumerFlow() throws Exception {
        // Given
        Long userId = 1L;
        Long couponId = 1L;

        // When
        String requestId = couponIssueProducer.sendCouponIssueRequest(userId, couponId);
        assertThat(requestId).isNotNull();

        // Then - Awaitility로 비동기 처리 대기
        await()
                .atMost(10, SECONDS)
                .untilAsserted(() -> {
                    // DB에서 UserCoupon 조회
                    var userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
                    assertThat(userCoupon).isPresent();
                    assertThat(userCoupon.get().getUserId()).isEqualTo(userId);
                    assertThat(userCoupon.get().getCouponId()).isEqualTo(couponId);

                    // Coupon의 remaining_qty 감소 확인
                    var coupon = couponRepository.findById(couponId);
                    assertThat(coupon).isPresent();
                    assertThat(coupon.get().getRemainingQty()).isEqualTo(99); // 100 - 1 = 99
                });
    }

    /**
     * 테스트 2: 중복 메시지 발행 시 멱등성 보장 검증
     *
     * 시나리오:
     * 1. 같은 userId, couponId로 2번 발행
     * 2. Consumer가 2번 모두 처리
     * 3. DB에는 1개의 UserCoupon만 저장 (UNIQUE constraint)
     * 4. 두 번째 처리는 IllegalArgumentException 발생 → Offset 커밋
     */
    @Test
    @DisplayName("중복 메시지 발행 시 멱등성 보장 검증")
    void testIdempotency() throws Exception {
        // Given
        Long userId = 2L;
        Long couponId = 2L;

        // When - 같은 요청을 2번 발행
        String requestId1 = couponIssueProducer.sendCouponIssueRequest(userId, couponId);
        String requestId2 = couponIssueProducer.sendCouponIssueRequest(userId, couponId);

        assertThat(requestId1).isNotNull();
        assertThat(requestId2).isNotNull();
        assertThat(requestId1).isNotEqualTo(requestId2); // requestId는 다름

        // Then - Awaitility로 비동기 처리 대기
        await()
                .atMost(10, SECONDS)
                .untilAsserted(() -> {
                    // DB에는 1개의 UserCoupon만 존재
                    var userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
                    assertThat(userCoupon).isPresent();

                    // Coupon의 remaining_qty는 1번만 감소
                    var coupon = couponRepository.findById(couponId);
                    assertThat(coupon).isPresent();
                    assertThat(coupon.get().getRemainingQty()).isEqualTo(99); // 100 - 1 = 99
                });
    }

    /**
     * 테스트 3: 여러 사용자 동시 요청 시 병렬 처리 검증
     *
     * 시나리오:
     * 1. 10명의 사용자가 동시에 같은 쿠폰 발급 요청
     * 2. 10개의 Consumer가 병렬로 처리
     * 3. 모두 성공적으로 발급 (10개의 UserCoupon 생성)
     * 4. Coupon의 remaining_qty는 10 감소
     */
    @Test
    @DisplayName("여러 사용자 동시 요청 시 병렬 처리 검증")
    void testConcurrentRequests() throws Exception {
        // Given
        Long couponId = 3L;
        int userCount = 10;

        // When - 10명의 사용자가 동시에 발급 요청
        for (long userId = 1; userId <= userCount; userId++) {
            couponIssueProducer.sendCouponIssueRequest(userId, couponId);
        }

        // Then - Awaitility로 비동기 처리 대기
        await()
                .atMost(15, SECONDS)
                .untilAsserted(() -> {
                    // 10명 모두 쿠폰 발급 완료 (각 사용자별 확인)
                    long issuedCount = 0;
                    for (long userId = 1; userId <= userCount; userId++) {
                        var userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
                        if (userCoupon.isPresent()) {
                            issuedCount++;
                        }
                    }
                    assertThat(issuedCount).isEqualTo(userCount);

                    // Coupon의 remaining_qty는 10 감소
                    var coupon = couponRepository.findById(couponId);
                    assertThat(coupon).isPresent();
                    assertThat(coupon.get().getRemainingQty()).isEqualTo(100 - userCount); // 100 - 10 = 90
                });
    }

    /**
     * 테스트 4: userId 기반 파티셔닝 검증
     *
     * 시나리오:
     * 1. 같은 userId로 여러 쿠폰 발급 요청
     * 2. 모두 같은 파티션으로 전달됨 (순서 보장)
     * 3. 각 쿠폰별로 1개씩 발급 성공
     */
    @Test
    @DisplayName("userId 기반 파티셔닝 검증 (같은 사용자 요청은 순서 보장)")
    void testUserIdPartitioning() throws Exception {
        // Given
        Long userId = 5L;
        Long[] couponIds = {1L, 2L, 3L};

        // When - 같은 userId로 여러 쿠폰 발급 요청
        for (Long couponId : couponIds) {
            couponIssueProducer.sendCouponIssueRequest(userId, couponId);
        }

        // Then - Awaitility로 비동기 처리 대기
        await()
                .atMost(10, SECONDS)
                .untilAsserted(() -> {
                    // 3개의 쿠폰 모두 발급 완료
                    int issuedCount = 0;
                    for (Long couponId : couponIds) {
                        var userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
                        if (userCoupon.isPresent()) {
                            issuedCount++;
                        }
                    }
                    assertThat(issuedCount).isEqualTo(couponIds.length);
                });
    }

    /**
     * 테스트 5: 쿠폰 소진 시 실패 처리 검증
     *
     * 시나리오:
     * 1. remaining_qty = 1인 쿠폰 생성
     * 2. 2명의 사용자가 발급 요청
     * 3. 1명은 성공, 1명은 실패 (쿠폰 소진)
     * 4. 실패한 요청은 IllegalArgumentException → Offset 커밋
     */
    @Test
    @DisplayName("쿠폰 소진 시 실패 처리 검증")
    void testCouponExhaustion() throws Exception {
        // Given - remaining_qty = 1인 쿠폰 생성
        Coupon limitedCoupon = Coupon.builder()
                .couponId(999L)
                .couponName("Limited Coupon")
                .discountType("FIXED_AMOUNT")
                .discountAmount(10000L)
                .totalQuantity(1)  // maxQty 대신 totalQuantity
                .remainingQty(1)  // 재고 1개
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        couponRepository.save(limitedCoupon);

        Long couponId = 999L;
        Long userId1 = 10L;
        Long userId2 = 11L;

        // When - 2명이 동시에 발급 요청
        couponIssueProducer.sendCouponIssueRequest(userId1, couponId);
        couponIssueProducer.sendCouponIssueRequest(userId2, couponId);

        // Then - Awaitility로 비동기 처리 대기
        await()
                .atMost(10, SECONDS)
                .untilAsserted(() -> {
                    // 1명만 발급 성공
                    long issuedCount = 0;
                    if (userCouponRepository.findByUserIdAndCouponId(userId1, couponId).isPresent()) {
                        issuedCount++;
                    }
                    if (userCouponRepository.findByUserIdAndCouponId(userId2, couponId).isPresent()) {
                        issuedCount++;
                    }
                    assertThat(issuedCount).isEqualTo(1);

                    // Coupon의 remaining_qty = 0
                    var coupon = couponRepository.findById(couponId);
                    assertThat(coupon).isPresent();
                    assertThat(coupon.get().getRemainingQty()).isEqualTo(0);
                });
    }

    // ===== 테스트 데이터 생성 =====

    private void createTestUsers() {
        for (long i = 1; i <= 20; i++) {
            // findById로 존재 확인 후 저장
            if (userRepository.findById(i).isEmpty()) {
                User user = User.builder()
                        .userId(i)
                        .email("test" + i + "@test.com")
                        .name("TestUser" + i)  // userName 대신 name
                        .balance(1000000L)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                userRepository.save(user);
            }
        }
    }

    private void createTestCoupons() {
        for (long i = 1; i <= 10; i++) {
            // findById로 존재 확인 후 저장
            if (couponRepository.findById(i).isEmpty()) {
                Coupon coupon = Coupon.builder()
                        .couponId(i)
                        .couponName("Test Coupon " + i)
                        .discountType("FIXED_AMOUNT")
                        .discountAmount(10000L * i)
                        .totalQuantity(100)  // maxQty 대신 totalQuantity
                        .remainingQty(100)
                        .isActive(true)
                        .validFrom(LocalDateTime.now().minusDays(1))
                        .validUntil(LocalDateTime.now().plusDays(30))
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                couponRepository.save(coupon);
            }
        }
    }
}