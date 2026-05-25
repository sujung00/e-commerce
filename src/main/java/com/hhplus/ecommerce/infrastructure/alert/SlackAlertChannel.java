package com.hhplus.ecommerce.infrastructure.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SlackAlertChannel - Slack Webhook 알림 채널 (stub 구현)
 *
 * 역할:
 * - Slack Webhook URL이 설정된 경우 사용되는 알림 채널
 * - 현재는 실제 HTTP 호출 없이 로그로 시뮬레이션 (stub)
 *
 * 프로덕션 확장:
 * - java.net.http.HttpClient로 Slack Incoming Webhook API 호출
 * - POST https://{webhookUrl} with JSON body {"text": "..."}
 *
 * 설정:
 * - application.yml: alert.slack.webhook-url
 * - AlertChannelConfig에서 Bean 등록 (webhook-url 존재 시)
 */
public class SlackAlertChannel implements AlertChannel {

    private static final Logger log = LoggerFactory.getLogger(SlackAlertChannel.class);

    private final String webhookUrl;

    public SlackAlertChannel(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /**
     * Slack 알림 발송 (stub - 로그 시뮬레이션)
     *
     * 실제 프로덕션 구현 예시:
     * <pre>
     * HttpClient client = HttpClient.newHttpClient();
     * String body = "{\"text\": \"[" + level + "] " + title + "\\n" + message + "\"}";
     * HttpRequest request = HttpRequest.newBuilder()
     *     .uri(URI.create(webhookUrl))
     *     .POST(HttpRequest.BodyPublishers.ofString(body))
     *     .header("Content-Type", "application/json")
     *     .build();
     * client.send(request, HttpResponse.BodyHandlers.ofString());
     * </pre>
     *
     * @param level   알림 수준 (ERROR, WARN, INFO)
     * @param title   알림 제목
     * @param message 알림 메시지
     */
    @Override
    public void send(String level, String title, String message) {
        // stub: 실제 Slack HTTP 호출 대신 로그로 시뮬레이션
        log.info("[SlackAlertChannel] Slack 알림 발송 시뮬레이션 - webhookUrl={}, level={}, title={}, message={}",
                webhookUrl, level, title, message);
    }
}
