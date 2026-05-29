package com.hhplus.ecommerce.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 메서드 활성화 설정 — 테스트 프로파일에서는 로드되지 않는다.
 *
 * 테스트 환경에서 스케줄러를 비활성화하는 이유:
 * - CouponQueueService.processCouponQueue() : 10ms 주기로 실행, non-daemon 스레드가
 *   JVM 종료를 막아 통합 테스트 hang의 원인이 된다.
 * - OutboxPollingService.pollAndSendMessages() : 5s 주기, Kafka 브로커 미존재 환경에서
 *   불필요한 연결 시도 및 에러 로그 발생.
 * - CouponQueueService.processRetryQueue() : 60s 주기, 위와 동일한 이유.
 *
 * BaseIntegrationTest 에 @ActiveProfiles("test") 가 선언되어 있으므로
 * 모든 통합 테스트에서 이 빈이 자동으로 제외된다.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
