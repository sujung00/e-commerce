package com.hhplus.ecommerce.infrastructure.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DataPlatformClient - 데이터 플랫폼 외부 연동 클라이언트
 *
 * 역할:
 * - 주문 완료 데이터를 외부 데이터 플랫폼으로 전송
 * - HTTP, Kafka 등 다양한 방식으로 확장 가능
 *
 * 현재 구현:
 * - 로깅 기반 stub (프로덕션에서는 실제 HTTP/Kafka 호출)
 *
 * 프로덕션 구현 예시:
 * - RestTemplate.postForObject()
 * - KafkaTemplate.send()
 * - WebClient.post()
 */
@Slf4j
@Component
public class DataPlatformClient {

    /**
     * 데이터 플랫폼으로 메시지 전송
     *
     * @param payload JSON 형태의 메시지 데이터
     * @throws Exception 전송 실패 시
     */
    public void send(String payload) throws Exception {
        log.info("[DataPlatformClient] 데이터 플랫폼으로 메시지 전송 시작");
        log.info("[DataPlatformClient] Payload: {}", payload);

        // TODO: 실제 구현
        // 방법 1: HTTP POST
        // restTemplate.postForObject("http://data-platform.example.com/api/events", payload, String.class);
        //
        // 방법 2: Kafka
        // kafkaTemplate.send("data-platform.events", payload).get();

        // 현재: 로깅만 수행 (stub)
        log.info("[DataPlatformClient] 데이터 플랫폼 전송 완료 (시뮬레이션)");
    }
}