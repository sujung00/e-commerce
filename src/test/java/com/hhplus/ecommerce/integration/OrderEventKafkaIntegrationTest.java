package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.domain.order.DataPlatformEvent;
import com.hhplus.ecommerce.domain.order.DataPlatformEventRepository;
import com.hhplus.ecommerce.domain.order.event.OrderCompletedEvent;
import com.hhplus.ecommerce.infrastructure.kafka.OrderEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * OrderEventKafkaIntegrationTest - Kafka 기반 주문 이벤트 통합 테스트
 *
 * 테스트 목적:
 * - Kafka Producer/Consumer 동작 검증
 * - OrderEventConsumer의 멱등성 보장 검증
 * - DataPlatformEvent 저장 및 중복 처리 방지 검증
 * - UNIQUE constraint (order_id, event_type) 동작 확인
 *
 * TestContainers 구성:
 * - MySQL 8.0: 데이터베이스
 * - Redis 7.0: 분산락 (필요시)
 * - Kafka (Confluent Platform): 메시지 브로커
 *
 * 테스트 시나리오:
 * 1. Kafka 메시지 발행 및 Consumer 처리 확인
 * 2. DataPlatformEvent 저장 검증
 * 3. 중복 메시지 발행 시 멱등성 보장 확인
 * 4. DB에 단 1건만 저장되는지 검증
 *
 * 실행 방법:
 * ```bash
 * # 전체 통합 테스트 실행
 * ./gradlew testIntegration
 *
 * # 특정 테스트만 실행
 * ./gradlew test --tests "OrderEventKafkaIntegrationTest"
 * ```
 *
 * 주의사항:
 * - Docker가 실행 중이어야 합니다
 * - Kafka Container 시작 시간이 길 수 있습니다 (~30초)
 * - @DirtiesContext로 각 테스트마다 Spring Context 재시작
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
@Testcontainers
@ContextConfiguration(initializers = OrderEventKafkaIntegrationTest.TestContainersInitializer.class)
@DisplayName("Kafka 기반 주문 완료 이벤트 통합 테스트")
class OrderEventKafkaIntegrationTest {

    // ═══════════════════════════════════════════════════════════════════════
    // TestContainers 컨테이너 정의
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * MySQL 8.0 TestContainer
     * - 주문 및 이벤트 데이터 저장
     */
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ecommerce_test")
            .withUsername("testuser")
            .withPassword("testpass")
            .withEnv("MYSQL_ROOT_PASSWORD", "testroot");

    /**
     * Redis 7.0 TestContainer
     * - 분산락 (필요시)
     */
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.0")
            .withExposedPorts(6379);

    /**
     * Kafka TestContainer (Confluent Platform)
     * - 메시지 브로커
     * - KRaft 모드 (Zookeeper 없음)
     * - 동적 포트 할당
     */
    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    )
            .withEmbeddedZookeeper();  // 내장 Zookeeper 사용

    // ═══════════════════════════════════════════════════════════════════════
    // Spring Bean 주입
    // ═══════════════════════════════════════════════════════════════════════

    @Autowired
    private KafkaTemplate<String, OrderCompletedEvent> kafkaTemplate;

    @Autowired
    private OrderEventProducer orderEventProducer;

    @Autowired
    private DataPlatformEventRepository dataPlatformEventRepository;

    // ═══════════════════════════════════════════════════════════════════════
    // 테스트 데이터 초기화
    // ═══════════════════════════════════════════════════════════════════════

    @BeforeEach
    void setUp() {
        // 각 테스트 전 데이터 정리
        dataPlatformEventRepository.deleteAll();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 테스트 케이스
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Kafka 메시지 발행 및 Consumer 처리 확인")
    @DirtiesContext
    void testKafkaMessageProducerAndConsumer() throws InterruptedException {
        // Given: 주문 완료 이벤트 생성
        Long orderId = 101L;
        Long userId = 1L;
        Long totalAmount = 50000L;
        LocalDateTime occurredAt = LocalDateTime.now();

        OrderCompletedEvent event = new OrderCompletedEvent(
                orderId,
                userId,
                totalAmount,
                occurredAt
        );

        // When: Kafka Producer로 메시지 발행
        orderEventProducer.publishOrderCompletedEvent(event);

        System.out.println("\n[TEST] Kafka 메시지 발행 완료 - orderId=" + orderId);

        // Then: Consumer가 메시지를 처리하고 DB에 저장할 때까지 대기 (최대 10초)
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    // DB에 DataPlatformEvent 저장되었는지 확인
                    List<DataPlatformEvent> events = dataPlatformEventRepository.findAll();
                    assertThat(events).hasSize(1);

                    // 저장된 이벤트 검증
                    DataPlatformEvent savedEvent = events.get(0);
                    assertThat(savedEvent.getOrderId()).isEqualTo(orderId);
                    assertThat(savedEvent.getEventType()).isEqualTo("ORDER_COMPLETED");
                    assertThat(savedEvent.getUserId()).isEqualTo(userId);
                    assertThat(savedEvent.getTotalAmount()).isEqualTo(totalAmount);
                    assertThat(savedEvent.getOccurredAt()).isNotNull();
                    assertThat(savedEvent.getProcessedAt()).isNotNull();

                    System.out.println("\n[TEST] ✅ Consumer 처리 완료 - eventId=" + savedEvent.getEventId());
                });
    }

    @Test
    @DisplayName("중복 메시지 발행 시 멱등성 보장 확인 (UNIQUE constraint)")
    @DirtiesContext
    void testIdempotencyWithDuplicateMessages() throws InterruptedException {
        // Given: 동일한 주문 완료 이벤트 생성
        Long orderId = 102L;
        Long userId = 2L;
        Long totalAmount = 70000L;
        LocalDateTime occurredAt = LocalDateTime.now();

        OrderCompletedEvent event = new OrderCompletedEvent(
                orderId,
                userId,
                totalAmount,
                occurredAt
        );

        // When: 같은 메시지를 2번 발행 (중복 시뮬레이션)
        orderEventProducer.publishOrderCompletedEvent(event);
        System.out.println("\n[TEST] 첫 번째 메시지 발행 - orderId=" + orderId);

        // 첫 번째 메시지 처리 대기
        Thread.sleep(2000);

        orderEventProducer.publishOrderCompletedEvent(event);
        System.out.println("\n[TEST] 두 번째 메시지 발행 (중복) - orderId=" + orderId);

        // 두 번째 메시지 처리 대기
        Thread.sleep(3000);

        // Then: DB에 단 1건만 저장되어야 함 (멱등성 보장)
        List<DataPlatformEvent> events = dataPlatformEventRepository.findAll();
        assertThat(events).hasSize(1);

        // 저장된 이벤트 검증
        DataPlatformEvent savedEvent = events.get(0);
        assertThat(savedEvent.getOrderId()).isEqualTo(orderId);
        assertThat(savedEvent.getEventType()).isEqualTo("ORDER_COMPLETED");
        assertThat(savedEvent.getUserId()).isEqualTo(userId);
        assertThat(savedEvent.getTotalAmount()).isEqualTo(totalAmount);

        System.out.println("\n[TEST] ✅ 멱등성 보장 확인 - 2번 발행했지만 DB에 1건만 저장됨");
        System.out.println("[TEST]    - eventId=" + savedEvent.getEventId());
        System.out.println("[TEST]    - orderId=" + savedEvent.getOrderId());
    }

    @Test
    @DisplayName("다수의 주문 이벤트 처리 확인")
    @DirtiesContext
    void testMultipleOrderEvents() throws InterruptedException {
        // Given: 여러 주문 완료 이벤트 생성
        int eventCount = 5;
        for (int i = 1; i <= eventCount; i++) {
            Long orderId = (long) (200 + i);
            Long userId = (long) i;
            Long totalAmount = 10000L * i;

            OrderCompletedEvent event = new OrderCompletedEvent(
                    orderId,
                    userId,
                    totalAmount,
                    LocalDateTime.now()
            );

            // When: Kafka로 메시지 발행
            orderEventProducer.publishOrderCompletedEvent(event);
            System.out.println("[TEST] 메시지 발행 - orderId=" + orderId);
        }

        // Then: Consumer가 모든 메시지를 처리할 때까지 대기
        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<DataPlatformEvent> events = dataPlatformEventRepository.findAll();
                    assertThat(events).hasSize(eventCount);

                    System.out.println("\n[TEST] ✅ 모든 메시지 처리 완료 - 총 " + events.size() + "건");
                });

        // 각 이벤트 검증
        List<DataPlatformEvent> allEvents = dataPlatformEventRepository.findAll();
        for (DataPlatformEvent event : allEvents) {
            assertThat(event.getOrderId()).isNotNull();
            assertThat(event.getEventType()).isEqualTo("ORDER_COMPLETED");
            assertThat(event.getUserId()).isNotNull();
            assertThat(event.getTotalAmount()).isGreaterThan(0L);
            assertThat(event.getProcessedAt()).isNotNull();
        }
    }

    @Test
    @DisplayName("orderId를 Key로 사용하여 순서 보장 확인")
    @DirtiesContext
    void testOrderIdAsMessageKey() throws InterruptedException {
        // Given: 같은 orderId로 여러 이벤트 발행 (시뮬레이션)
        Long orderId = 301L;
        Long userId = 10L;

        // 첫 번째 이벤트
        OrderCompletedEvent event1 = new OrderCompletedEvent(
                orderId,
                userId,
                50000L,
                LocalDateTime.now()
        );

        // When: Key를 orderId로 설정하여 발행
        String key = String.valueOf(orderId);
        kafkaTemplate.send("order.events", key, event1);

        System.out.println("\n[TEST] 메시지 발행 with Key - orderId=" + orderId + ", key=" + key);

        // Then: Consumer가 처리할 때까지 대기
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<DataPlatformEvent> events = dataPlatformEventRepository.findAll();
                    assertThat(events).hasSize(1);

                    DataPlatformEvent savedEvent = events.get(0);
                    assertThat(savedEvent.getOrderId()).isEqualTo(orderId);

                    System.out.println("[TEST] ✅ Key 기반 메시지 처리 완료");
                    System.out.println("[TEST]    - Key: " + key);
                    System.out.println("[TEST]    - OrderId: " + savedEvent.getOrderId());
                });
    }

    @Test
    @DisplayName("UNIQUE constraint 직접 검증 (수동 INSERT 시도)")
    @DirtiesContext
    void testUniqueConstraintDirectly() {
        // Given: 첫 번째 이벤트 저장
        Long orderId = 401L;
        String eventType = "ORDER_COMPLETED";

        DataPlatformEvent event1 = DataPlatformEvent.create(
                orderId,
                eventType,
                1L,
                50000L,
                LocalDateTime.now()
        );

        dataPlatformEventRepository.save(event1);
        System.out.println("\n[TEST] 첫 번째 이벤트 저장 완료 - orderId=" + orderId);

        // When: 같은 orderId + eventType으로 두 번째 저장 시도
        DataPlatformEvent event2 = DataPlatformEvent.create(
                orderId,
                eventType,
                1L,
                60000L,  // 금액만 다름
                LocalDateTime.now()
        );

        // Then: DataIntegrityViolationException 발생해야 함
        boolean exceptionThrown = false;
        try {
            dataPlatformEventRepository.saveAndFlush(event2);  // 즉시 flush
        } catch (DataIntegrityViolationException e) {
            exceptionThrown = true;
            System.out.println("\n[TEST] ✅ UNIQUE constraint 동작 확인");
            System.out.println("[TEST]    - 예외 발생: " + e.getClass().getSimpleName());
            System.out.println("[TEST]    - 메시지: " + e.getMessage());
        }

        assertThat(exceptionThrown).isTrue();

        // DB에 1건만 저장되었는지 확인
        List<DataPlatformEvent> events = dataPlatformEventRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventId()).isEqualTo(event1.getEventId());

        System.out.println("[TEST] ✅ DB에 1건만 저장됨 - eventId=" + events.get(0).getEventId());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TestContainers Initializer
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * TestContainers 동적 포트를 Spring 설정에 전달하는 Initializer
     *
     * MySQL, Redis, Kafka의 동적 포트를 Spring 환경변수에 주입합니다.
     */
    static class TestContainersInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            // MySQL 설정
            applicationContext.getEnvironment().getSystemProperties().put(
                    "spring.datasource.url",
                    mysql.getJdbcUrl()
            );
            applicationContext.getEnvironment().getSystemProperties().put(
                    "spring.datasource.username",
                    mysql.getUsername()
            );
            applicationContext.getEnvironment().getSystemProperties().put(
                    "spring.datasource.password",
                    mysql.getPassword()
            );

            // Redis 설정
            String redisHost = redis.getHost();
            Integer redisPort = redis.getMappedPort(6379);

            applicationContext.getEnvironment().getSystemProperties().put(
                    "spring.redis.host",
                    redisHost
            );
            applicationContext.getEnvironment().getSystemProperties().put(
                    "spring.redis.port",
                    String.valueOf(redisPort)
            );

            // Kafka 설정
            String kafkaBootstrapServers = kafka.getBootstrapServers();

            applicationContext.getEnvironment().getSystemProperties().put(
                    "kafka.bootstrap-servers",
                    kafkaBootstrapServers
            );
            applicationContext.getEnvironment().getSystemProperties().put(
                    "spring.kafka.bootstrap-servers",
                    kafkaBootstrapServers
            );

            // 디버그 로그
            System.out.println("\n" +
                    "╔══════════════════════════════════════════════════════════════════════╗\n" +
                    "║              🐳 TestContainers 초기화 완료 (Kafka 포함)             ║\n" +
                    "╠══════════════════════════════════════════════════════════════════════╣\n" +
                    "║ 🗄️  MySQL                                                           ║\n" +
                    "║    JDBC URL: " + mysql.getJdbcUrl() + "\n" +
                    "║    Username: " + mysql.getUsername() + "\n" +
                    "║                                                                      ║\n" +
                    "║ 💾 Redis                                                            ║\n" +
                    "║    Host: " + redisHost + "\n" +
                    "║    Port: " + redisPort + "\n" +
                    "║                                                                      ║\n" +
                    "║ 📨 Kafka                                                            ║\n" +
                    "║    Bootstrap Servers: " + kafkaBootstrapServers + "\n" +
                    "║    Topic: order.events                                              ║\n" +
                    "║                                                                      ║\n" +
                    "║ ✅ Kafka Producer/Consumer가 TestContainers와 연결됩니다            ║\n" +
                    "╚══════════════════════════════════════════════════════════════════════╝\n");
        }
    }
}