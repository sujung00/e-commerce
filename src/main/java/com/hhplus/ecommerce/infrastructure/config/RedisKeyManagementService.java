package com.hhplus.ecommerce.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis 키 관리 서비스
 *
 * 역할:
 * - Redis 키를 카테고리별로 관리 및 모니터링
 * - 메모리 사용량 추적
 * - 키 검색 및 패턴 매칭
 * - 키 메타데이터 조회
 *
 * 사용 예:
 * - redisKeyManagementService.getAllKeysGroupedByCategory()
 * - redisKeyManagementService.getMemoryUsageByCategory()
 * - redisKeyManagementService.getKeysByCategory(RedisKeyCategory.CACHE)
 * - redisKeyManagementService.getKeyMetadata(RedisKeyType.CACHE_COUPON_LIST)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisKeyManagementService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 모든 Redis 키를 카테고리별로 그룹화
     *
     * @return 카테고리 → 키 목록 맵
     */
    public Map<RedisKeyCategory, List<String>> getAllKeysGroupedByCategory() {
        Map<RedisKeyCategory, List<String>> result = new HashMap<>();

        for (RedisKeyType keyType : RedisKeyType.values()) {
            String pattern = keyType.getPattern();
            Set<String> keys = redisTemplate.keys(pattern);

            if (keys != null && !keys.isEmpty()) {
                RedisKeyCategory category = keyType.getCategory();
                result.computeIfAbsent(category, k -> new ArrayList<>())
                      .addAll(keys);
            }
        }

        return result;
    }

    /**
     * 특정 카테고리의 모든 키 조회
     *
     * @param category 카테고리
     * @return 키 목록
     */
    public List<String> getKeysByCategory(RedisKeyCategory category) {
        List<String> result = new ArrayList<>();

        for (RedisKeyType keyType : RedisKeyType.values()) {
            if (keyType.getCategory() == category) {
                String pattern = keyType.getPattern();
                Set<String> keys = redisTemplate.keys(pattern);
                if (keys != null) {
                    result.addAll(keys);
                }
            }
        }

        return result;
    }

    /**
     * 카테고리별 키 개수
     *
     * @return 카테고리 → 키 개수 맵
     */
    public Map<String, Integer> getKeyCountByCategory() {
        Map<String, Integer> result = new HashMap<>();

        for (RedisKeyType keyType : RedisKeyType.values()) {
            String pattern = keyType.getPattern();
            Set<String> keys = redisTemplate.keys(pattern);
            int count = keys != null ? keys.size() : 0;

            if (count > 0) {
                String categoryName = keyType.getCategory().getDisplayName();
                result.merge(categoryName, count, Integer::sum);
            }
        }

        return result;
    }

    /**
     * RedisKeyType별 메타데이터 정보
     *
     * @param keyType Redis 키 타입
     * @return 메타데이터 맵
     */
    public Map<String, Object> getKeyMetadata(RedisKeyType keyType) {
        Map<String, Object> metadata = new LinkedHashMap<>();

        metadata.put("name", keyType.getName());
        metadata.put("description", keyType.getDescription());
        metadata.put("pattern", keyType.getPattern());
        metadata.put("category", keyType.getCategory().getDisplayName());
        metadata.put("ttl", keyType.getTtl() != null ?
            keyType.getTtl().getSeconds() + " seconds" : "none (persistent)");

        // 실제 키 개수
        Set<String> keys = redisTemplate.keys(keyType.getPattern());
        metadata.put("actual_count", keys != null ? keys.size() : 0);

        // 샘플 키 1개로 MEMORY USAGE 명령 실행 → 실제 메모리 조회
        if (keys != null && !keys.isEmpty()) {
            String sampleKey = keys.iterator().next();
            try {
                Long memBytes = fetchMemoryUsage(sampleKey);
                metadata.put("actual_memory_bytes", memBytes != null ? memBytes : 0L);
            } catch (Exception e) {
                log.warn("[RedisKeyManagementService] MEMORY USAGE 조회 실패: key={}", sampleKey, e);
                metadata.put("actual_memory_bytes", 0L);
            }
        }

        return metadata;
    }

    /**
     * 모든 RedisKeyType의 메타데이터 조회
     *
     * @return 키 타입별 메타데이터
     */
    public Map<String, Map<String, Object>> getAllKeyMetadata() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();

        for (RedisKeyType keyType : RedisKeyType.values()) {
            result.put(keyType.name(), getKeyMetadata(keyType));
        }

        return result;
    }

    /**
     * 카테고리별 예상 메모리 사용량 (대략적)
     *
     * @return 카테고리별 메모리 사용량 (바이트)
     */
    public Map<String, Long> getEstimatedMemoryByCategory() {
        Map<String, Long> result = new HashMap<>();

        for (RedisKeyType keyType : RedisKeyType.values()) {
            Set<String> keys = redisTemplate.keys(keyType.getPattern());
            if (keys != null && !keys.isEmpty()) {
                String categoryName = keyType.getCategory().getDisplayName();

                try {
                    // 샘플 키 1개로 MEMORY USAGE 실측 후 전체 추산 (같은 카테고리의 키가 유사한 크기라 가정)
                    String sampleKey = keys.iterator().next();
                    Long sampleBytes = fetchMemoryUsage(sampleKey);

                    if (sampleBytes != null && sampleBytes > 0) {
                        long estimatedTotal = sampleBytes * keys.size();
                        result.merge(categoryName, estimatedTotal, Long::sum);
                    }
                } catch (Exception e) {
                    log.warn("[RedisKeyManagementService] 카테고리별 메모리 측정 실패: {}", categoryName, e);
                }
            }
        }

        return result;
    }

    /**
     * 특정 패턴의 키 검색
     *
     * @param pattern 검색 패턴 (예: "cache:*")
     * @return 매칭된 키 목록
     */
    public List<String> searchKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        return keys != null ? new ArrayList<>(keys) : new ArrayList<>();
    }

    /**
     * TTL 정보를 포함한 키 상세 정보
     *
     * @param key Redis 키
     * @return 키의 상세 정보 (TTL, 타입, 메모리 사용량)
     */
    public Map<String, Object> getKeyDetails(String key) {
        Map<String, Object> details = new LinkedHashMap<>();

        details.put("key", key);

        // TTL 조회
        Long ttl = redisTemplate.getExpire(key);
        details.put("ttl_seconds", ttl != null && ttl >= 0 ? ttl : "no expiration");

        // 데이터 타입
        String type = String.valueOf(redisTemplate.type(key));
        details.put("type", type);

        // MEMORY USAGE 명령으로 실제 메모리 사용량 조회
        try {
            Long memBytes = fetchMemoryUsage(key);
            details.put("actual_memory_bytes", memBytes != null ? memBytes : 0L);
        } catch (Exception e) {
            log.warn("[RedisKeyManagementService] MEMORY USAGE 조회 실패: key={}", key, e);
            details.put("actual_memory_bytes", 0L);
        }

        // 대응하는 RedisKeyType 찾기
        for (RedisKeyType keyType : RedisKeyType.values()) {
            if (matchesPattern(key, keyType.getPattern())) {
                details.put("key_type", keyType.name());
                details.put("category", keyType.getCategory().getDisplayName());
                details.put("description", keyType.getDescription());
                break;
            }
        }

        return details;
    }

    /**
     * Redis MEMORY USAGE 명령으로 키의 실제 메모리 사용량 조회
     *
     * opsForValue().size() 는 직렬화된 문자열 길이(바이트)만 반환하여 실제 Redis 메모리와 다르다.
     * MEMORY USAGE 명령은 키 자체의 메타데이터·인코딩·만료 정보를 포함한 실제 메모리를 반환한다.
     * (Redis 4.0+, Spring Data Redis conn.execute() native command API 사용)
     *
     * @param key Redis 키
     * @return 실제 메모리 사용량 (바이트). 키 미존재 또는 명령 미지원 시 null
     */
    private Long fetchMemoryUsage(String key) {
        return redisTemplate.execute((RedisCallback<Long>) conn -> {
            Object result = conn.execute("MEMORY",
                    "USAGE".getBytes(StandardCharsets.UTF_8),
                    key.getBytes(StandardCharsets.UTF_8));
            return result instanceof Long ? (Long) result : null;
        });
    }

    /**
     * 키가 패턴과 일치하는지 확인
     *
     * @param key 실제 키
     * @param pattern 패턴 (예: "cache:coupon:*")
     * @return 일치 여부
     */
    private boolean matchesPattern(String key, String pattern) {
        // Redis 패턴: * = 모든 문자, ? = 하나의 문자
        // 간단한 와일드카드 매칭 (정규표현식으로 변환)
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");
        return key.matches(regex);
    }

    /**
     * Redis 키 일관성 검증
     *
     * 예상되는 모든 RedisKeyType이 정의되어 있는지 확인
     *
     * @return 검증 결과 (문제 있으면 경고 메시지, 문제 없으면 "OK")
     */
    public String validateKeyConsistency() {
        List<String> issues = new ArrayList<>();

        for (RedisKeyType keyType : RedisKeyType.values()) {
            if (keyType.getPattern() == null || keyType.getPattern().isEmpty()) {
                issues.add(String.format("%s: pattern is empty", keyType.name()));
            }

            if (keyType.getCategory() == null) {
                issues.add(String.format("%s: category is null", keyType.name()));
            }

            // TTL이 null인 경우 (QUEUE는 일반적으로 null)
            if (keyType.getCategory() == RedisKeyCategory.QUEUE && keyType.getTtl() != null) {
                // QUEUE는 TTL이 없어야 함 (명시적 pop까지 유지)
                issues.add(String.format("%s: QUEUE should not have TTL", keyType.name()));
            }
        }

        if (issues.isEmpty()) {
            log.info("✅ Redis key consistency validation passed");
            return "OK";
        } else {
            String report = String.join("\n  - ", issues);
            log.warn("⚠️ Redis key consistency issues found:\n  - {}", report);
            return "ISSUES_FOUND:\n  - " + report;
        }
    }

    /**
     * 전체 시스템 상태 리포트
     *
     * @return 상태 리포트 (문자열)
     */
    public String getSystemStatusReport() {
        StringBuilder report = new StringBuilder();

        report.append("=== Redis Key Management System Status Report ===\n\n");

        // 1. 키 카운트
        report.append("1. Key Count by Category:\n");
        Map<String, Integer> keyCount = getKeyCountByCategory();
        keyCount.forEach((category, count) ->
            report.append(String.format("   - %s: %d keys\n", category, count)));
        report.append(String.format("   - Total: %d keys\n\n",
            keyCount.values().stream().mapToInt(Integer::intValue).sum()));

        // 2. 메모리 사용량
        report.append("2. Estimated Memory Usage by Category:\n");
        Map<String, Long> memory = getEstimatedMemoryByCategory();
        memory.forEach((category, bytes) ->
            report.append(String.format("   - %s: %.2f MB\n", category, bytes / (1024.0 * 1024.0))));
        report.append(String.format("   - Total: %.2f MB\n\n",
            memory.values().stream().mapToLong(Long::longValue).sum() / (1024.0 * 1024.0)));

        // 3. 일관성 검증
        report.append("3. Key Consistency:\n");
        report.append("   - ").append(validateKeyConsistency().replace("\n", "\n   - ")).append("\n");

        return report.toString();
    }
}
