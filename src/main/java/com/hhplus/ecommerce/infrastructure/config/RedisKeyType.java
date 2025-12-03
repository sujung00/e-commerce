package com.hhplus.ecommerce.infrastructure.config;

import java.time.Duration;

/**
 * Redis 키 타입 관리 Enum
 *
 * 목표:
 * 1. 모든 Redis 키를 한 곳에서 관리
 * 2. 도메인별 분류 (캐시, 락, 큐, 정렬, 상태)
 * 3. TTL을 포함한 메타데이터 관리
 * 4. IDE 자동완성으로 개발 효율 향상
 *
 * 사용법:
 * - KEY: RedisKeyType.CACHE_COUPON_LIST.getKey()
 * - 파라미터: RedisKeyType.CACHE_USER_COUPONS.buildKey(userId, status)
 * - TTL: RedisKeyType.CACHE_COUPON_LIST.getTtl()
 * - PATTERN: RedisKeyType.CACHE_COUPON_LIST.getPattern()
 */
public enum RedisKeyType {

    // ===== 캐시 (Cache) - 조회 성능 향상 =====

    CACHE_COUPON_LIST(
        "cache:coupon:list",
        RedisKeyCategory.CACHE,
        Duration.ofMinutes(30),
        "발급 가능한 쿠폰 목록",
        "모든 활성화된 쿠폰 조회 결과"
    ),

    CACHE_COUPON_DETAIL(
        "cache:coupon:detail:{couponId}",
        RedisKeyCategory.CACHE,
        Duration.ofMinutes(30),
        "쿠폰 상세정보",
        "특정 쿠폰의 상세 정보 (재고, 할인율 등)"
    ),

    CACHE_ACTIVE_COUPONS(
        "cache:active:coupons",
        RedisKeyCategory.CACHE,
        Duration.ofMinutes(30),
        "활성 쿠폰 목록",
        "재고가 남아있는 활성화된 쿠폰 목록"
    ),

    CACHE_USER_COUPONS(
        "cache:user:coupons:{userId}:{status}",
        RedisKeyCategory.CACHE,
        Duration.ofMinutes(5),
        "사용자 쿠폰 목록",
        "특정 사용자의 상태별 쿠폰 목록 (발급, 사용, 만료 등)"
    ),

    CACHE_PRODUCT_LIST(
        "cache:product:list",
        RedisKeyCategory.CACHE,
        Duration.ofHours(1),
        "상품 목록",
        "모든 상품 목록"
    ),

    CACHE_PRODUCT_DETAIL(
        "cache:product:detail:{productId}",
        RedisKeyCategory.CACHE,
        Duration.ofHours(2),
        "상품 상세정보",
        "특정 상품의 상세 정보"
    ),

    CACHE_POPULAR_PRODUCTS(
        "cache:popular:products",
        RedisKeyCategory.CACHE,
        Duration.ofHours(1),
        "인기 상품",
        "현재 인기 있는 상품 목록"
    ),

    // ===== 분산 락 (Distributed Lock) - 동시성 제어 =====

    LOCK_COUPON_ISSUE(
        "lock:coupon:{couponId}:user:{userId}",
        RedisKeyCategory.LOCK,
        Duration.ofSeconds(2),
        "쿠폰 발급 락",
        "쿠폰 발급 시 사용자별 동시 요청 차단 (사용 중단)"
    ),

    LOCK_PRODUCT_STOCK(
        "lock:product:{productId}:stock",
        RedisKeyCategory.LOCK,
        Duration.ofSeconds(2),
        "상품 재고 락",
        "상품 주문 시 재고 차감 동시성 제어"
    ),

    // ===== 정렬 (Sorted Set) - 순위 계산 =====

    ZSET_RANKING_DAILY(
        "ranking:daily:{date}",
        RedisKeyCategory.SORTED_SET,
        Duration.ofDays(30),
        "일일 상품 랭킹",
        "날짜별 주문량 기준 상품 랭킹 (Sorted Set, 내림차순)"
    ),

    ZSET_RANKING_WEEKLY(
        "ranking:weekly:{week}",
        RedisKeyCategory.SORTED_SET,
        Duration.ofDays(90),
        "주간 상품 랭킹",
        "주차별 주문량 기준 상품 랭킹 (Sorted Set)"
    ),

    // ===== 큐 (Queue) - 비동기 작업 처리 =====

    QUEUE_COUPON_PENDING(
        "queue:coupon:pending",
        RedisKeyCategory.QUEUE,
        null,  // TTL 없음 (명시적 pop까지 유지)
        "쿠폰 발급 대기 큐",
        "발급 대기 중인 쿠폰 요청 (FIFO, LPUSH/RPOP)"
    ),

    QUEUE_COUPON_RETRY(
        "queue:coupon:retry",
        RedisKeyCategory.QUEUE,
        null,  // TTL 없음
        "쿠폰 발급 재시도 큐",
        "시스템 오류로 재시도 대기 중인 요청"
    ),

    QUEUE_COUPON_DLQ(
        "queue:coupon:dlq",
        RedisKeyCategory.QUEUE,
        null,  // TTL 없음 (수동 처리까지 유지)
        "쿠폰 발급 Dead Letter Queue",
        "최대 재시도 횟수를 초과한 실패 요청 (수동 개입 필요)"
    ),

    QUEUE_ORDER_NOTIFICATION(
        "queue:order:notification",
        RedisKeyCategory.QUEUE,
        null,
        "주문 알림 큐",
        "사용자에게 전송할 주문 알림 메시지"
    ),

    // ===== 상태 추적 (State) - 요청 상태 관리 =====

    STATE_COUPON_REQUEST(
        "state:coupon:request:{requestId}",
        RedisKeyCategory.STATE,
        Duration.ofMinutes(30),
        "쿠폰 요청 상태",
        "비동기 쿠폰 발급 요청의 처리 상태 (PENDING, COMPLETED, FAILED, RETRY)"
    ),

    STATE_COUPON_RESULT(
        "state:coupon:result:{requestId}",
        RedisKeyCategory.STATE,
        Duration.ofHours(24),
        "쿠폰 발급 결과",
        "쿠폰 발급 완료 후 결과 JSON 저장"
    ),

    STATE_ORDER_PROCESSING(
        "state:order:processing:{orderId}",
        RedisKeyCategory.STATE,
        Duration.ofHours(24),
        "주문 처리 상태",
        "주문 처리 과정의 실시간 상태"
    ),

    STATE_ORDER_PAYMENT(
        "state:order:payment:{orderId}",
        RedisKeyCategory.STATE,
        Duration.ofMinutes(5),
        "주문 결제 상태",
        "주문 결제 진행 중인 상태 (결제 게이트웨이 요청 처리 상태)"
    ),

    STATE_ORDER_LOCK(
        "state:order:lock:{orderId}",
        RedisKeyCategory.STATE,
        Duration.ofSeconds(60),
        "주문 처리 락 상태",
        "주문 처리 중 동시성 제어를 위한 상태 플래그"
    ),

    // ===== 세션 (Session) - 사용자 세션 관리 =====

    SESSION_USER(
        "session:user:{sessionId}",
        RedisKeyCategory.SESSION,
        Duration.ofHours(24),
        "사용자 세션",
        "로그인한 사용자의 세션 정보"
    ),

    // ===== 기타 (Other) =====

    COUNTER_API_RATE_LIMIT(
        "counter:ratelimit:{userId}:{endpoint}",
        RedisKeyCategory.OTHER,
        Duration.ofMinutes(1),
        "API 레이트 제한",
        "사용자별 API 호출 횟수 제한"
    );

    private final String pattern;
    private final RedisKeyCategory category;
    private final Duration ttl;
    private final String name;
    private final String description;

    RedisKeyType(String pattern, RedisKeyCategory category, Duration ttl,
                 String name, String description) {
        this.pattern = pattern;
        this.category = category;
        this.ttl = ttl;
        this.name = name;
        this.description = description;
    }

    /**
     * 실제 키 생성 (플레이스홀더 치환)
     *
     * 예:
     * CACHE_USER_COUPONS.buildKey(123L, "UNUSED")
     * → "cache:user:coupons:123:UNUSED"
     *
     * @param values 플레이스홀더를 치환할 값들 (순서대로)
     * @return 실제 키 문자열
     */
    public String buildKey(Object... values) {
        String key = this.pattern;
        for (Object value : values) {
            key = key.replaceFirst("\\{[^}]*\\}", String.valueOf(value));
        }
        return key;
    }

    /**
     * 키 조회 (정적 키의 경우)
     *
     * 예: CACHE_COUPON_LIST.getKey() → "cache:coupon:list"
     *
     * @return 실제 키 문자열
     * @throws IllegalStateException 파라미터가 필요한 경우
     */
    public String getKey() {
        if (pattern.contains("{")) {
            throw new IllegalStateException(
                String.format("%s requires parameters: %s", name, pattern)
            );
        }
        return pattern;
    }

    public String getPattern() {
        return pattern;
    }

    public RedisKeyCategory getCategory() {
        return category;
    }

    public Duration getTtl() {
        return ttl;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 디버깅용 정보 출력
     */
    @Override
    public String toString() {
        return String.format("[%s] %s (category=%s, ttl=%s, pattern=%s)",
            this.name(),
            this.name,
            this.category.getDisplayName(),
            this.ttl != null ? this.ttl.getSeconds() + "s" : "none",
            this.pattern);
    }
}
