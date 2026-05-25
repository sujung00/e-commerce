package com.hhplus.ecommerce.infrastructure.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AlertChannelConfig - 알림 채널 Bean 설정
 *
 * alert.slack.webhook-url이 설정된 경우 SlackAlertChannel 사용,
 * 설정되지 않은 경우 LogAlertChannel(폴백)을 사용한다.
 *
 * application.yml 설정 예시:
 * <pre>
 * alert:
 *   slack:
 *     webhook-url: https://hooks.slack.com/services/XXX/YYY/ZZZ  # Slack Incoming Webhook URL
 * </pre>
 */
@Configuration
public class AlertChannelConfig {

    private static final Logger log = LoggerFactory.getLogger(AlertChannelConfig.class);

    /**
     * Slack Webhook URL (alert.slack.webhook-url)
     * - 설정된 경우: SlackAlertChannel 활성화
     * - 미설정 시 빈 문자열로 기본값 처리
     */
    @Value("${alert.slack.webhook-url:}")
    private String slackWebhookUrl;

    /**
     * AlertChannel Bean 등록
     *
     * - webhook-url이 설정된 경우: SlackAlertChannel (stub - 로그 시뮬레이션)
     * - webhook-url이 없는 경우: LogAlertChannel (SLF4J Logger 폴백)
     *
     * @return AlertChannel 구현체
     */
    @Bean
    public AlertChannel alertChannel() {
        if (slackWebhookUrl != null && !slackWebhookUrl.isBlank()) {
            log.info("[AlertChannelConfig] SlackAlertChannel 활성화 - webhookUrl={}", slackWebhookUrl);
            return new SlackAlertChannel(slackWebhookUrl);
        }
        log.info("[AlertChannelConfig] LogAlertChannel 활성화 (폴백) - alert.slack.webhook-url 미설정");
        return new LogAlertChannel();
    }
}
