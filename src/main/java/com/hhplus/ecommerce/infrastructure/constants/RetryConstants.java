package com.hhplus.ecommerce.infrastructure.constants;

/**
 * RetryConstants - 재시도 설정 기본값 상수
 *
 * @deprecated application.yml 의 {@code retry:} 섹션과
 *             {@link RetryProperties} 빈을 통해 설정값을 주입받는 방식으로 전환됨.
 *             Spring 빈 컨텍스트 밖(예: 테스트 유틸)에서만 이 클래스를 사용하라.
 *             서비스 계층에서는 {@link RetryProperties} 를 주입해 사용할 것.
 *
 * <p>각 상수는 {@code application.yml retry:} 섹션의 기본값과 동일하다.</p>
 */
@Deprecated
public class RetryConstants {

    // ========== Order Creation Retry Constants ==========

    /** 주문 생성 재시도 최대 횟수 */
    public static final int ORDER_CREATION_MAX_RETRIES = 3;

    /** 주문 생성 재시도 초기 딜레이 (밀리초) */
    public static final long ORDER_CREATION_INITIAL_DELAY_MS = 50L;

    /** 주문 생성 재시도 지수 백오프 배수 (2배 증가) */
    public static final int ORDER_CREATION_BACKOFF_MULTIPLIER = 2;

    /** 주문 생성 재시도 최대 딜레이 (밀리초) */
    public static final long ORDER_CREATION_MAX_DELAY_MS = 1000L;

    // ========== Coupon Issuance Retry Constants ==========

    /** 쿠폰 발급 재시도 최대 횟수 (무한 반복 방지: 3회로 제한) */
    public static final int COUPON_ISSUANCE_MAX_RETRIES = 3;

    /** 쿠폰 발급 재시도 초기 딜레이 (밀리초) */
    public static final long COUPON_ISSUANCE_INITIAL_DELAY_MS = 5L;

    /** 쿠폰 발급 재시도 지수 백오프 배수 (2배 증가) */
    public static final int COUPON_ISSUANCE_BACKOFF_MULTIPLIER = 2;

    // ========== Outbox Event Polling Retry Constants ==========

    /** Outbox 폴링 재시도 최대 횟수 */
    public static final int OUTBOX_POLLING_MAX_RETRIES = 3;

    /** Outbox 폴링 재시도 딜레이 (초) */
    public static final long OUTBOX_POLLING_RETRY_DELAY_SECONDS = 60L;

    /** Outbox 폴링 주기 (밀리초) */
    public static final long OUTBOX_POLLING_INTERVAL_MS = 5000L;

    // ========== General Retry Constants ==========

    /** 일반 재시도 최대 횟수 (기본값) */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /** 일반 재시도 초기 딜레이 (밀리초, 기본값) */
    public static final long DEFAULT_INITIAL_DELAY_MS = 100L;

    // ========== Retry Calculation Utilities ==========
    /*
     * 지수 백오프(Exponential Backoff) 계산 방식:
     *
     * 재시도 횟수 | 딜레이 계산 | 실제 딜레이
     * ========================================
     * 1회차      | 50ms * (2^0) = 50ms         | 50ms
     * 2회차      | 50ms * (2^1) = 100ms        | 100ms
     * 3회차      | 50ms * (2^2) = 200ms (최대 1000ms) | 200ms
     *
     * 계산 공식:
     * delay = min(initialDelay * pow(multiplier, retryCount), maxDelay)
     *
     * 구현 예:
     * long delay = Math.min(
     *     INITIAL_DELAY_MS * (1L << retryCount),  // 2의 거듭제곱
     *     MAX_DELAY_MS
     * );
     * Thread.sleep(delay);
     */

    private RetryConstants() {
        throw new AssertionError("RetryConstants는 인스턴스화할 수 없습니다");
    }
}
