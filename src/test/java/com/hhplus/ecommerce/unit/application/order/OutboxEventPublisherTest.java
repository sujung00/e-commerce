package com.hhplus.ecommerce.unit.application.order;

import com.hhplus.ecommerce.application.order.OutboxEventPublisher;
import com.hhplus.ecommerce.application.shipping.client.ShippingServiceClient;
import com.hhplus.ecommerce.domain.order.Outbox;
import com.hhplus.ecommerce.domain.order.OutboxRepository;
import com.hhplus.ecommerce.domain.order.event.OrderCompletedEvent;
import com.hhplus.ecommerce.infrastructure.external.DataPlatformClient;
import com.hhplus.ecommerce.unit.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * OutboxEventPublisher 단위 테스트
 * - ORDER_COMPLETED, ORDER_CANCELLED, PAYMENT_COMPLETED, PAYMENT_SUCCESS
 *   각 타입에 대해 KafkaTemplate.send()가 올바른 토픽으로 호출되는지 검증
 */
@DisplayName("[Unit] OutboxEventPublisher - Kafka 발행 검증")
class OutboxEventPublisherTest extends BaseUnitTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private DataPlatformClient dataPlatformClient;

    @Mock
    private ShippingServiceClient shippingServiceClient;

    @Mock
    private KafkaTemplate<String, OrderCompletedEvent> kafkaTemplate;

    private OutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxEventPublisher(outboxRepository, dataPlatformClient,
                shippingServiceClient, kafkaTemplate);
    }

    /**
     * KafkaTemplate stub 설정 (Kafka를 사용하는 테스트에서만 호출)
     */
    private void stubKafka() {
        SendResult<String, OrderCompletedEvent> sendResult = new SendResult<>(null, null);
        CompletableFuture<SendResult<String, OrderCompletedEvent>> future =
                CompletableFuture.completedFuture(sendResult);
        given(kafkaTemplate.send(anyString(), anyString(), any(OrderCompletedEvent.class)))
                .willReturn(future);
    }

    private Outbox buildOutbox(String messageType) {
        return Outbox.builder()
                .messageId(1L)
                .orderId(100L)
                .userId(10L)
                .messageType(messageType)
                .payload("{\"orderId\":100}")
                .status("PENDING")
                .retryCount(0)
                .build();
    }

    @Test
    @DisplayName("ORDER_COMPLETED: order.completed 토픽으로 Kafka 발행")
    void orderCompleted_sendsToKafka() throws Exception {
        stubKafka();
        Outbox outbox = buildOutbox("ORDER_COMPLETED");

        publisher.publish(outbox);

        verify(kafkaTemplate).send(eq("order.completed"), eq("100"), any(OrderCompletedEvent.class));
    }

    @Test
    @DisplayName("ORDER_CANCELLED: order.cancelled 토픽으로 Kafka 발행")
    void orderCancelled_sendsToKafka() throws Exception {
        stubKafka();
        Outbox outbox = buildOutbox("ORDER_CANCELLED");

        publisher.publish(outbox);

        verify(kafkaTemplate).send(eq("order.cancelled"), eq("100"), any(OrderCompletedEvent.class));
    }

    @Test
    @DisplayName("PAYMENT_COMPLETED: payment.completed 토픽으로 Kafka 발행")
    void paymentCompleted_sendsToKafka() throws Exception {
        stubKafka();
        Outbox outbox = buildOutbox("PAYMENT_COMPLETED");

        publisher.publish(outbox);

        verify(kafkaTemplate).send(eq("payment.completed"), eq("100"), any(OrderCompletedEvent.class));
    }

    @Test
    @DisplayName("PAYMENT_SUCCESS: payment.success 토픽으로 Kafka 발행")
    void paymentSuccess_sendsToKafka() throws Exception {
        stubKafka();
        Outbox outbox = buildOutbox("PAYMENT_SUCCESS");

        publisher.publish(outbox);

        verify(kafkaTemplate).send(eq("payment.success"), eq("100"), any(OrderCompletedEvent.class));
    }

    @Test
    @DisplayName("DATA_PLATFORM: DataPlatformClient.send() 호출")
    void dataPlatform_callsDataPlatformClient() throws Exception {
        Outbox outbox = buildOutbox("DATA_PLATFORM");

        publisher.publish(outbox);

        verify(dataPlatformClient).send(any());
    }

    @Test
    @DisplayName("알 수 없는 메시지 타입: IllegalArgumentException 발생")
    void unknownType_throwsException() {
        Outbox outbox = buildOutbox("UNKNOWN_TYPE");

        assertThatCode(() -> publisher.publish(outbox))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN_TYPE");
    }
}
