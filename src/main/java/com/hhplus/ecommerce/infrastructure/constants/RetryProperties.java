package com.hhplus.ecommerce.infrastructure.constants;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 재시도 관련 설정값을 application.yml 에서 주입받는 ConfigurationProperties.
 *
 * 대응 YAML 경로:
 * <pre>
 * retry:
 *   order:
 *     max-attempts: 3
 *     initial-delay-ms: 50
 *     backoff-multiplier: 2
 *     max-delay-ms: 1000
 *   coupon:
 *     issuance:
 *       max-attempts: 3
 *       initial-delay-ms: 5
 *       backoff-multiplier: 2
 *     queue:
 *       fixed-rate-ms: 10
 *       max-batch-size: 10
 *       retry-fixed-rate-ms: 60000
 *       retry-initial-delay-ms: 30000
 *       retry-batch-size: 5
 *   outbox:
 *     max-attempts: 3
 *     retry-delay-seconds: 60
 *     polling-interval-ms: 5000
 * </pre>
 *
 * @see RetryConstants 기존 하드코딩 상수 (deprecated)
 */
@Component
@ConfigurationProperties(prefix = "retry")
@Getter
@Setter
public class RetryProperties {

    private Order order = new Order();
    private Coupon coupon = new Coupon();
    private Outbox outbox = new Outbox();

    /** 주문 생성 낙관적 락 재시도 설정 */
    @Getter
    @Setter
    public static class Order {
        /** 최대 재시도 횟수 (기본 3) */
        private int maxAttempts = 3;
        /** 초기 대기 시간 밀리초 (기본 50ms) */
        private long initialDelayMs = 50L;
        /** 지수 백오프 배수 (기본 2) */
        private int backoffMultiplier = 2;
        /** 최대 대기 시간 밀리초 (기본 1000ms) */
        private long maxDelayMs = 1000L;
    }

    /** 쿠폰 관련 재시도 설정 */
    @Getter
    @Setter
    public static class Coupon {
        private Issuance issuance = new Issuance();
        private Queue queue = new Queue();

        /** 쿠폰 발급 비관적 락 재시도 설정 */
        @Getter
        @Setter
        public static class Issuance {
            /** 최대 재시도 횟수 (기본 3) */
            private int maxAttempts = 3;
            /** 초기 대기 시간 밀리초 (기본 5ms) */
            private long initialDelayMs = 5L;
            /** 지수 백오프 배수 (기본 2) */
            private int backoffMultiplier = 2;
        }

        /** 쿠폰 Redis 큐 워커 설정 */
        @Getter
        @Setter
        public static class Queue {
            /** 메인 워커 실행 주기 밀리초 (기본 10ms) */
            private long fixedRateMs = 10L;
            /** 한 번에 처리할 최대 쿠폰 수 (기본 10) */
            private int maxBatchSize = 10;
            /** 재시도 워커 실행 주기 밀리초 (기본 60000ms = 1분) */
            private long retryFixedRateMs = 60_000L;
            /** 재시도 워커 초기 대기 밀리초 (기본 30000ms = 30초) */
            private long retryInitialDelayMs = 30_000L;
            /** 재시도 워커 1회 처리 최대 건수 (기본 5) */
            private int retryBatchSize = 5;
        }
    }

    /** Outbox 폴링 재시도 설정 */
    @Getter
    @Setter
    public static class Outbox {
        /** 최대 재시도 횟수 (기본 3) */
        private int maxAttempts = 3;
        /** 재시도 간격 초 (기본 60초) */
        private long retryDelaySeconds = 60L;
        /** 폴링 주기 밀리초 (기본 5000ms = 5초) */
        private long pollingIntervalMs = 5_000L;
    }
}
