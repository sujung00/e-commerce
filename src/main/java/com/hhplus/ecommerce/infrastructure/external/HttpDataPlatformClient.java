package com.hhplus.ecommerce.infrastructure.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HttpDataPlatformClient - java.net.http.HttpClient 기반 데이터 플랫폼 전송 구현체
 *
 * 역할:
 * - data-platform.base-url이 설정된 경우 HTTP POST로 이벤트 전송
 * - fire-and-forget: 실패 시 로그만 남기고 예외를 전파하지 않음
 *
 * 전송 방식:
 * - POST {base-url}/events
 * - Content-Type: application/json
 * - Body: payload (JSON string)
 *
 * 외부 의존성 없음 (java.net.http.HttpClient 표준 라이브러리 사용)
 *
 * 설정:
 * - application.yml: data-platform.base-url
 * - DataPlatformClientConfig에서 Bean 등록
 */
public class HttpDataPlatformClient implements DataPlatformClient {

    private static final Logger log = LoggerFactory.getLogger(HttpDataPlatformClient.class);

    private final String baseUrl;
    private final HttpClient httpClient;

    public HttpDataPlatformClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 데이터 플랫폼으로 HTTP POST 전송 (fire-and-forget)
     *
     * 실패 시 예외를 전파하지 않고 로그만 남긴다.
     * - 비즈니스 트랜잭션에 영향을 주지 않음
     * - Outbox 배치가 재시도 보장
     *
     * @param payload JSON 형태의 메시지 데이터
     */
    @Override
    public void send(String payload) {
        log.info("[HttpDataPlatformClient] 데이터 플랫폼으로 HTTP POST 전송 시작 - url={}", baseUrl + "/events");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/events"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[HttpDataPlatformClient] 전송 성공 - statusCode={}", response.statusCode());
            } else {
                log.warn("[HttpDataPlatformClient] 전송 실패 (비정상 응답) - statusCode={}, body={}",
                        response.statusCode(), response.body());
            }
        } catch (Exception e) {
            // fire-and-forget: 예외를 전파하지 않음
            log.error("[HttpDataPlatformClient] 전송 실패 (예외 무시) - error={}", e.getMessage(), e);
        }
    }
}
