package com.hhplus.ecommerce.infrastructure.config;

/**
 * Redis 키 카테고리
 *
 * 역할:
 * - Redis 키를 목적별로 분류
 * - 도메인별 관리 및 모니터링
 * - 메모리 사용량 추적
 */
public enum RedisKeyCategory {
    CACHE("캐시", "조회 성능 향상용, @Cacheable 관련"),
    LOCK("분산 락", "동시성 제어용, Redisson 분산락"),
    SORTED_SET("정렬", "순위/랭킹, Sorted Set 자료구조"),
    QUEUE("큐", "비동기 처리, List 자료구조"),
    STATE("상태 추적", "요청/주문 상태, String/Hash 자료구조"),
    SESSION("세션", "사용자 세션, String 자료구조"),
    OTHER("기타", "기타 용도");

    private final String displayName;
    private final String description;

    RedisKeyCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
