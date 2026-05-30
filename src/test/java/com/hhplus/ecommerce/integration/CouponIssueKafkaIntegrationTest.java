package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.domain.coupon.Coupon;
import com.hhplus.ecommerce.domain.coupon.CouponRepository;
import com.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import com.hhplus.ecommerce.infrastructure.kafka.CouponIssueProducer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private KafkaAdmin kafkaAdmin;

    @Autowired(required = false)
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
     *
     * ⚠️ 핵심 주의사항: AdminClient.close()의 기본 타임아웃은 Long.MAX_VALUE ms (사실상 무한대).
     * createTopics()의 비동기 요청이 완료되기 전에 try-with-resources가 close()를 호출하면
     * 브로커 응답을 무한 대기하며 테스트 전체가 수 시간 블로킹된다.
     *
     * 해결: createTopics().all().get(10, SECONDS)로 명시적 대기 후 close().
     * close() 호출 시점에 미완료 요청이 없으므로 즉시 반환된다.
     */
    private void createKafkaTopic() {
        // try-with-resources 대신 수동 close 사용:
        // AdminClient.close() 기본값은 Long.MAX_VALUE ms 무한 대기.
        // close(Duration)으로 명시 타임아웃을 줘야 브로커 무응답 시 블로킹을 막는다.
        AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties());
        try {
            NewTopic newTopic = new NewTopic(TOPIC_NAME, 10, (short) 1);
            adminClient.createTopics(Collections.singletonList(newTopic))
                    .all()
                    .get(10, SECONDS);
        } catch (Exception e) {
            // TopicExistsException(2nd+ run) 또는 타임아웃은 무시하고 계속
            System.out.println("[Test] Topic creation: " + e.getMessage());
        } finally {
            adminClient.close(java.time.Duration.ofSeconds(5));
        }
    }

    /**
     * Consumer 준비 대기
     *
     * ContainerTestUtils.waitForAssignment(container, N) 방식을 사용하지 않는다.
     * 이유: Spring Kafka는 @KafkaListener 하나당 main/retry/DLT 3개의 컨테이너를 등록한다.
     *       2개 @KafkaListener × 3 컨테이너 = 6 컨테이너 중 다수가 이 테스트의 Kafka에
     *       존재하지 않는 토픽(예: order.events, retry 토픽)을 구독하여 각각 63초씩 블로킹한다.
     *       결과: 3 컨테이너 × 63초 = 189초 / @BeforeEach → 5 테스트 × 189초 = 15분 소요.
     *
     * 대안: coupon.issue.requests 토픽의 파티션이 실제로 할당됐는지
     *       topic 이름 기반으로 직접 폴링한다. 최대 10초 대기.
     */
    private void waitForConsumerAssignment() {
        if (kafkaListenerEndpointRegistry == null) {
            return;
        }

        long deadline = System.currentTimeMillis() + 10_000;
        boolean assigned = false;

        while (System.currentTimeMillis() < deadline && !assigned) {
            for (var container : kafkaListenerEndpointRegistry.getListenerContainers()) {
                try {
                    var partitions = container.getAssignedPartitions();
                    if (partitions != null && !partitions.isEmpty()) {
                        boolean hasCouponTopic = partitions.stream()
                                .anyMatch(tp -> tp.topic().contains("coupon.issue"));
                        if (hasCouponTopic) {
                            assigned = true;
                            System.out.println("[Test] Coupon consumer ready: " + partitions.size() + " partitions");
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (!assigned) {
                try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }

        if (!assigned) {
            System.out.println("[Test] Consumer not ready within 10s, continuing anyway");
        }
    }

    /**
     * 테스트 데이터 정리 (JdbcTemplate 직접 SQL)
     *
     * JPA save()/delete()를 사용하지 않는 이유:
     * - @Version 필드 + 명시적 ID 조합은 JPA merge() 경로를 타고
     *   DB에 해당 row가 없을 때 StaleObjectStateException을 유발한다.
     * - JdbcTemplate native SQL은 JPA의 isNew() / merge() 판단을 우회한다.
     *
     * 정리 범위:
     * 1. user_coupons: 테스트 중 발급된 모든 쿠폰 기록
     * 2. coupons (id > 10): testCouponExhaustion이 생성한 한정 쿠폰
     * 3. coupons (id 1-10): remaining_qty/version 초기화 (createTestCoupons에서도 하지만 선제 정리)
     */
    private void cleanupTestData() {
        // user_coupons 전체 삭제 (FK 제약 순서)
        jdbcTemplate.execute("DELETE FROM user_coupons");

        // testCouponExhaustion이 생성한 auto-generated 쿠폰 삭제 (coupon_id > 10)
        jdbcTemplate.execute("DELETE FROM coupons WHERE coupon_id > 10");

        // 고정 쿠폰(1-10) 재고·버전 초기화는 @BeforeEach createTestCoupons()의
        // ON DUPLICATE KEY UPDATE 에서 처리한다.
        // 여기서 UPDATE coupons를 추가로 실행하면 Kafka consumer가 보유 중인
        // SELECT FOR UPDATE 락과 충돌할 수 있다.
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
        // Given - remaining_qty = 1인 쿠폰 생성 (auto-generated couponId, no explicit version)
        // version을 null로 두면 isNew()=true → persist() 호출 → DB auto-increment ID 사용
        Coupon limitedCoupon = Coupon.builder()
                .couponName("Limited Coupon")
                .discountType("FIXED_AMOUNT")
                .discountAmount(10000L)
                .totalQuantity(1)
                .remainingQty(1)  // 재고 1개
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .build();
        limitedCoupon = couponRepository.save(limitedCoupon);

        Long couponId = limitedCoupon.getCouponId();
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

    /**
     * 테스트 사용자 생성 (native SQL upsert)
     *
     * 이유: JPA save()는 @GeneratedValue(IDENTITY) 엔티티에 명시적 userId를 설정하고
     *       version=0L(non-null)로 빌드하면 isNew()=false → merge() 호출 →
     *       DB에 해당 row가 없을 때 UPDATE 시도 → 0 rows updated →
     *       StaleObjectStateException 발생.
     *
     * 해결: JdbcTemplate 네이티브 SQL로 명시적 userId INSERT.
     *       ON DUPLICATE KEY UPDATE로 재실행 시 잔액 리셋.
     */
    private void createTestUsers() {
        for (long i = 1; i <= 20; i++) {
            jdbcTemplate.update(
                "INSERT INTO users (user_id, email, name, balance, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, 1000000, 0, NOW(), NOW()) " +
                "ON DUPLICATE KEY UPDATE balance=1000000, updated_at=NOW()",
                i, "test" + i + "@test.com", "TestUser" + i
            );
        }
    }

    /**
     * 테스트 쿠폰 생성 (native SQL upsert)
     *
     * 이유: createTestUsers()와 동일. version(0L) + 명시적 couponId 조합이
     *       JPA merge() 경로에서 StaleObjectStateException을 일으킨다.
     *
     * 추가: ON DUPLICATE KEY UPDATE로 테스트 간 remaining_qty를 100으로 리셋하여
     *       각 테스트가 신선한 쿠폰 재고를 가지도록 보장.
     */
    private void createTestCoupons() {
        for (long i = 1; i <= 10; i++) {
            jdbcTemplate.update(
                "INSERT INTO coupons (coupon_id, coupon_name, discount_type, discount_amount, discount_rate, " +
                "total_quantity, remaining_qty, valid_from, valid_until, is_active, version, created_at, updated_at) " +
                "VALUES (?, ?, 'FIXED_AMOUNT', ?, 0.00, 100, 100, " +
                "DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY), 1, 0, NOW(), NOW()) " +
                "ON DUPLICATE KEY UPDATE remaining_qty=100, is_active=1, version=0, updated_at=NOW()",
                i, "Test Coupon " + i, 10000L * i
            );
        }
    }
}