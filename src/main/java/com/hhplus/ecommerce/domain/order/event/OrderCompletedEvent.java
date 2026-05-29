package com.hhplus.ecommerce.domain.order.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 주문 완료 이벤트
 * 주문이 성공적으로 완료되었을 때 발행되는 도메인 이벤트
 */
@Getter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderCompletedEvent extends ApplicationEvent {

    private final Long orderId;
    private final Long userId;
    private final Long totalAmount;
    private final String recipientName;
    private final String shippingAddress;
    private final LocalDateTime occurredAt;

    /**
     * 주문 완료 이벤트 생성 (현재 시간 자동 설정)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param totalAmount 총 주문 금액
     */
    public OrderCompletedEvent(Long orderId, Long userId, Long totalAmount) {
        super(orderId);
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.recipientName = null;
        this.shippingAddress = null;
        this.occurredAt = LocalDateTime.now();
    }

    /**
     * 주문 완료 이벤트 생성 (배송 정보 포함)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param totalAmount 총 주문 금액
     * @param recipientName 수령인 이름
     * @param shippingAddress 배송 주소
     */
    public OrderCompletedEvent(Long orderId, Long userId, Long totalAmount, String recipientName, String shippingAddress) {
        super(orderId);
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.recipientName = recipientName;
        this.shippingAddress = shippingAddress;
        this.occurredAt = LocalDateTime.now();
    }

    /**
     * 주문 완료 이벤트 생성 (테스트용 - 명시적 시간 설정)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param totalAmount 총 주문 금액
     * @param occurredAt 이벤트 발생 시간
     */
    public OrderCompletedEvent(Long orderId, Long userId, Long totalAmount, LocalDateTime occurredAt) {
        super(orderId);
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.recipientName = null;
        this.shippingAddress = null;
        this.occurredAt = occurredAt;
    }

    /**
     * 주문 완료 이벤트 생성 (배송 정보 + 명시적 시간 설정)
     * Jackson JSON 역직렬화를 위한 @JsonCreator 생성자 (Kafka Consumer용)
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @param totalAmount 총 주문 금액
     * @param recipientName 수령인 이름
     * @param shippingAddress 배송 주소
     * @param occurredAt 이벤트 발생 시간
     */
    @JsonCreator
    public OrderCompletedEvent(
            @JsonProperty("orderId") Long orderId,
            @JsonProperty("userId") Long userId,
            @JsonProperty("totalAmount") Long totalAmount,
            @JsonProperty("recipientName") String recipientName,
            @JsonProperty("shippingAddress") String shippingAddress,
            @JsonProperty("occurredAt") LocalDateTime occurredAt) {
        super(orderId);
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.recipientName = recipientName;
        this.shippingAddress = shippingAddress;
        this.occurredAt = occurredAt;
    }
}
