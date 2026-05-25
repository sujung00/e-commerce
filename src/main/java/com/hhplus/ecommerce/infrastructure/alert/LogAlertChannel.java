package com.hhplus.ecommerce.infrastructure.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LogAlertChannel - SLF4J Logger 기반 폴백 알림 채널
 *
 * 역할:
 * - Slack Webhook URL이 설정되지 않은 경우 사용되는 폴백 채널
 * - SLF4J Logger를 통한 로그 출력만 수행
 *
 * 사용 시나리오:
 * - 로컬 개발 환경 (alert.slack.webhook-url 미설정)
 * - Slack 통합이 비활성화된 환경
 * - 테스트 환경
 *
 * 설정:
 * - AlertChannelConfig에서 webhook-url 미설정 시 Bean 등록
 */
public class LogAlertChannel implements AlertChannel {

    private static final Logger log = LoggerFactory.getLogger(LogAlertChannel.class);

    /**
     * 로그 기반 알림 출력
     *
     * @param level   알림 수준 (ERROR, WARN, INFO)
     * @param title   알림 제목
     * @param message 알림 메시지
     */
    @Override
    public void send(String level, String title, String message) {
        String formatted = "[AlertChannel:LOG] [{}] {} - {}";
        switch (level) {
            case "ERROR":
                log.error(formatted, level, title, message);
                break;
            case "WARN":
                log.warn(formatted, level, title, message);
                break;
            default:
                log.info(formatted, level, title, message);
                break;
        }
    }
}
