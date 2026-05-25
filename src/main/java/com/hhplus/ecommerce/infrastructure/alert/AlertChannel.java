package com.hhplus.ecommerce.infrastructure.alert;

/**
 * AlertChannel - 알림 채널 인터페이스
 *
 * 역할:
 * - 다양한 알림 채널(Slack, 로그, 이메일 등)을 추상화
 * - AlertService에서 주입받아 사용 (Strategy 패턴)
 *
 * 구현체:
 * - SlackAlertChannel: Slack Webhook을 통한 알림 (stub - 로그 시뮬레이션)
 * - LogAlertChannel: SLF4J Logger 기반 폴백 알림
 */
public interface AlertChannel {

    /**
     * 알림 발송
     *
     * @param level   알림 수준 (ERROR, WARN, INFO)
     * @param title   알림 제목
     * @param message 알림 메시지
     */
    void send(String level, String title, String message);
}
