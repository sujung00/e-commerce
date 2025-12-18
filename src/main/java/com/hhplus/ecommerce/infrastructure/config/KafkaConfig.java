package com.hhplus.ecommerce.infrastructure.config;

import com.hhplus.ecommerce.domain.coupon.event.CouponIssueRequest;
import com.hhplus.ecommerce.domain.order.event.OrderCompletedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * KafkaConfig - Kafka Producer/Consumer 설정
 *
 * KRaft 모드 기준:
 * - Zookeeper 없이 Kafka Controller가 메타데이터 관리
 * - bootstrap-servers로 Kafka 브로커에 직접 연결
 *
 * Producer 설정:
 * - JSON 직렬화로 OrderCompletedEvent 객체 전송
 * - acks=all: 모든 ISR 복제 완료 후 ack
 * - enable.idempotence=true: 멱등성 보장
 * - retries=3: 전송 실패 시 최대 3회 재시도
 *
 * Consumer 설정:
 * - JSON 역직렬화로 OrderCompletedEvent 객체 수신
 * - enable-auto-commit=false: 수동 커밋으로 at-least-once 보장
 * - auto-offset-reset=earliest: 처음부터 메시지 읽기
 * - Consumer Group: ecommerce-order-consumer-group
 */
@EnableKafka
@Configuration
public class KafkaConfig {

    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.consumer.group-id}")
    private String consumerGroupId;

    @Value("${kafka.consumer.coupon-group-id}")
    private String couponConsumerGroupId;

    /**
     * Kafka Producer 설정
     *
     * Key: String (orderId)
     * Value: OrderCompletedEvent (JSON 직렬화)
     *
     * 주요 설정:
     * - acks=all: Leader + 모든 ISR Follower가 복제 완료 후 ack
     * - retries=3: 전송 실패 시 3회 재시도
     * - enable.idempotence=true: 중복 메시지 방지 (멱등성)
     * - compression.type=snappy: 네트워크 대역폭 절약
     * - linger.ms=10: 10ms 동안 배치로 모아서 전송
     */
    @Bean
    public ProducerFactory<String, OrderCompletedEvent> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // 안정성 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // 성능 최적화
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * KafkaTemplate Bean
     *
     * Spring에서 Kafka Producer를 쉽게 사용하기 위한 템플릿
     * DataPlatformEventListener에서 주입받아 메시지 발행
     */
    @Bean
    public KafkaTemplate<String, OrderCompletedEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Kafka Consumer 설정
     *
     * Key: String (orderId)
     * Value: OrderCompletedEvent (JSON 역직렬화)
     *
     * 주요 설정:
     * - group-id: ecommerce-order-consumer-group (Consumer Group)
     * - auto-offset-reset=earliest: 처음부터 메시지 읽기
     * - enable-auto-commit=false: 수동 커밋 (처리 성공 후 커밋)
     * - trusted.packages: JSON 역직렬화 보안 설정
     */
    @Bean
    public ConsumerFactory<String, OrderCompletedEvent> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // Offset 관리
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // 성능 설정
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);

        // JSON 역직렬화 보안 설정 (신뢰할 수 있는 패키지만 허용)
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.hhplus.ecommerce.domain.order.event");
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderCompletedEvent.class.getName());

        return new DefaultKafkaConsumerFactory<>(
            configProps,
            new StringDeserializer(),
            new JsonDeserializer<>(OrderCompletedEvent.class)
        );
    }

    /**
     * Kafka Listener Container Factory
     *
     * @KafkaListener가 사용하는 컨테이너 팩토리
     *
     * 주요 설정:
     * - AckMode.MANUAL: 수동 커밋 모드
     *   - Consumer에서 명시적으로 acknowledge() 호출 시에만 커밋
     *   - 메시지 처리 실패 시 재처리 보장 (at-least-once)
     * - concurrency=3: 3개의 Consumer 스레드로 병렬 처리
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCompletedEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderCompletedEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        // 수동 커밋 설정 (at-least-once 보장)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // 동시성 설정 (3개의 Consumer 스레드)
        factory.setConcurrency(3);

        return factory;
    }

    // ===== Coupon Issue Request 설정 =====

    /**
     * Coupon Producer 설정
     *
     * Key: String (userId)
     * Value: CouponIssueRequest (JSON 직렬화)
     *
     * 주요 설정:
     * - userId를 Key로 사용하여 파티셔닝 (같은 사용자 요청은 같은 파티션)
     * - enable.idempotence=true: 중복 메시지 방지 (멱등성)
     * - acks=all: 모든 ISR 복제 완료 후 ack
     */
    @Bean
    public ProducerFactory<String, CouponIssueRequest> couponProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // 안정성 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // 성능 최적화
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Coupon KafkaTemplate Bean
     *
     * CouponIssueProducer에서 주입받아 메시지 발행
     */
    @Bean
    public KafkaTemplate<String, CouponIssueRequest> couponKafkaTemplate() {
        return new KafkaTemplate<>(couponProducerFactory());
    }

    /**
     * Coupon Consumer 설정
     *
     * Key: String (userId)
     * Value: CouponIssueRequest (JSON 역직렬화)
     *
     * 주요 설정:
     * - group-id: ecommerce-coupon-consumer-group (Consumer Group)
     * - auto-offset-reset=earliest: 처음부터 메시지 읽기
     * - enable-auto-commit=false: 수동 커밋 (처리 성공 후 커밋)
     * - trusted.packages: JSON 역직렬화 보안 설정
     */
    @Bean
    public ConsumerFactory<String, CouponIssueRequest> couponConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, couponConsumerGroupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // Offset 관리
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // 성능 설정
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);

        // JSON 역직렬화 보안 설정 (신뢰할 수 있는 패키지만 허용)
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES,
            "com.hhplus.ecommerce.domain.order.event,com.hhplus.ecommerce.domain.coupon.event");
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CouponIssueRequest.class.getName());

        return new DefaultKafkaConsumerFactory<>(
            configProps,
            new StringDeserializer(),
            new JsonDeserializer<>(CouponIssueRequest.class)
        );
    }

    /**
     * Coupon Listener Container Factory
     *
     * @KafkaListener가 사용하는 컨테이너 팩토리 (쿠폰 발급용)
     *
     * 주요 설정:
     * - AckMode.MANUAL: 수동 커밋 모드
     * - concurrency=10: 10개의 Consumer 스레드로 병렬 처리
     *   - 10개 파티션 + 10개 Consumer = 최대 병렬도
     *   - 설계 문서 기준: 200 req/s (P=10)
     */
    @Bean(name = "couponKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, CouponIssueRequest> couponKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CouponIssueRequest> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(couponConsumerFactory());

        // 수동 커밋 설정 (at-least-once 보장)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // 동시성 설정 (10개의 Consumer 스레드)
        // 설계 문서: 10개 파티션 기준
        factory.setConcurrency(10);

        return factory;
    }
}