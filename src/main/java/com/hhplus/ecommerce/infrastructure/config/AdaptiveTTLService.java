package com.hhplus.ecommerce.infrastructure.config;

import org.springframework.stereotype.Service;
import java.time.Duration;

/**
 * AdaptiveTTLService - 상태별 동적 TTL 관리 서비스
 *
 * 목표:
 * 1. 상태 키의 TTL을 동적으로 결정
 * 2. 사용 패턴에 맞게 메모리 효율화
 * 3. Redis 메모리 사용량 최소화
 *
 * TTL 정책:
 * - STATE_COUPON_REQUEST: 30분 (비동기 쿠폰 요청 처리 상태)
 * - STATE_ORDER_PAYMENT: 5분 (결제 진행 상태)
 * - STATE_ORDER_LOCK: 60초 (임시 락 상태)
 *
 * 사용 패턴 분석:
 * - 쿠폰 요청: 보통 1-2분 이내 완료, 롤백 가능성 고려 30분
 * - 결제 상태: 보통 1-3분 이내 완료, 거래 타임아웃 기준 5분
 * - 락 상태: 매우 단기 (밀리초~초 단위), 데드락 방지 60초
 */
@Service
public class AdaptiveTTLService {

    /**
     * 상태 키의 타입에 따라 적절한 TTL을 반환
     *
     * @param keyType RedisKeyType enum value
     * @return 해당 키 타입의 TTL Duration
     */
    public Duration getTTL(RedisKeyType keyType) {
        if (keyType.getTtl() == null) {
            return null;  // TTL 없음
        }
        return keyType.getTtl();
    }

    /**
     * 상태 키 이름으로 TTL을 반환 (문자열 기반)
     *
     * @param stateName 상태 키 이름 (예: "STATE_COUPON_REQUEST")
     * @return 해당 상태의 TTL Duration
     */
    public Duration getTTLByStateName(String stateName) {
        try {
            RedisKeyType keyType = RedisKeyType.valueOf(stateName);
            return getTTL(keyType);
        } catch (IllegalArgumentException e) {
            // 존재하지 않는 상태 키인 경우
            throw new IllegalArgumentException("Unknown state key: " + stateName, e);
        }
    }

    /**
     * 상태 키의 TTL을 초(seconds) 단위로 반환
     *
     * @param keyType RedisKeyType enum value
     * @return TTL (초), TTL이 없으면 -1 반환
     */
    public long getTTLSeconds(RedisKeyType keyType) {
        Duration ttl = getTTL(keyType);
        return ttl != null ? ttl.getSeconds() : -1;
    }

    /**
     * 상태 키의 TTL을 밀리초(milliseconds) 단위로 반환
     *
     * @param keyType RedisKeyType enum value
     * @return TTL (밀리초), TTL이 없으면 -1 반환
     */
    public long getTTLMillis(RedisKeyType keyType) {
        Duration ttl = getTTL(keyType);
        return ttl != null ? ttl.toMillis() : -1;
    }

    /**
     * STATE 카테고리 키의 TTL 정보를 조회
     *
     * @param keyType STATE 카테고리 RedisKeyType
     * @return TTL 정보 (StateKeyTTLInfo)
     */
    public StateKeyTTLInfo getStateKeyTTLInfo(RedisKeyType keyType) {
        if (keyType.getCategory() != RedisKeyCategory.STATE) {
            throw new IllegalArgumentException("Not a STATE category key: " + keyType.getName());
        }

        return new StateKeyTTLInfo(
            keyType.getName(),
            keyType.getDescription(),
            getTTL(keyType),
            getTTLSeconds(keyType)
        );
    }

    /**
     * 모든 STATE 카테고리 키의 TTL 정보를 조회
     *
     * @return STATE 카테고리의 모든 키 TTL 정보
     */
    public java.util.List<StateKeyTTLInfo> getAllStatekeysInfo() {
        return java.util.Arrays.stream(RedisKeyType.values())
            .filter(key -> key.getCategory() == RedisKeyCategory.STATE)
            .map(this::getStateKeyTTLInfo)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 동적 TTL 조정 (사용 패턴 변화에 대응)
     *
     * 현재는 고정 TTL을 반환하지만, 향후 다음 요소를 고려하여 동적 조정 가능:
     * - 시간대별 트래픽 패턴
     * - 시스템 부하
     * - Redis 메모리 사용률
     *
     * @param keyType RedisKeyType
     * @param systemLoadFactor 시스템 부하 (0.0 ~ 1.0)
     * @return 조정된 TTL
     */
    public Duration getAdaptiveTTL(RedisKeyType keyType, double systemLoadFactor) {
        Duration baseTTL = getTTL(keyType);
        if (baseTTL == null) {
            return null;
        }

        // 시스템 부하가 높으면 TTL을 50% 줄임 (메모리 확보)
        if (systemLoadFactor > 0.8) {
            return baseTTL.dividedBy(2);
        }

        // 정상 상태에서는 기본 TTL 반환
        return baseTTL;
    }

    /**
     * STATE 카테고리 키의 TTL 정보 DTO
     */
    public static class StateKeyTTLInfo {
        private final String keyName;
        private final String description;
        private final Duration ttl;
        private final long ttlSeconds;

        public StateKeyTTLInfo(String keyName, String description, Duration ttl, long ttlSeconds) {
            this.keyName = keyName;
            this.description = description;
            this.ttl = ttl;
            this.ttlSeconds = ttlSeconds;
        }

        public String getKeyName() {
            return keyName;
        }

        public String getDescription() {
            return description;
        }

        public Duration getTtl() {
            return ttl;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        @Override
        public String toString() {
            return String.format(
                "[%s] %s → TTL: %ds (%s)",
                keyName,
                description,
                ttlSeconds,
                ttl != null ? ttl.toString() : "none"
            );
        }
    }
}
