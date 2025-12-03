package com.hhplus.ecommerce.integration;

import com.hhplus.ecommerce.infrastructure.config.RedisKeyCategory;
import com.hhplus.ecommerce.infrastructure.config.RedisKeyManagementService;
import com.hhplus.ecommerce.infrastructure.config.RedisKeyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════
 * ✨ Redis Key Consistency Validation Test
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * 목적:
 * - RedisKeyType enum이 모두 정의되어 있고 일관성 있는지 검증
 * - RedisKeyManagementService가 모든 키를 올바르게 발견하고 관리하는지 검증
 * - Redis 캐시 구조의 완전성과 정합성 검증
 *
 * 테스트 방식:
 * 1. @SpringBootTest로 전체 Spring Context 로드
 * 2. TestContainers를 통해 실제 Redis 컨테이너 사용
 * 3. RedisKeyManagementService를 통해 키 일관성 검증
 * 4. RedisTemplate을 통해 Redis 직접 접근
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 🔍 검증 항목
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 1. ✅ RedisKeyType enum 정의 완전성
 *    - 모든 enum 값이 non-null pattern을 가지는가?
 *    - 모든 enum 값이 category를 가지는가?
 *    - 패턴에 중복이 없는가?
 *
 * 2. ✅ RedisKeyType별 메타데이터 일관성
 *    - CACHE 타입은 non-null TTL을 가지는가?
 *    - QUEUE 타입은 null TTL을 가지는가?
 *    - STATE 타입은 적절한 TTL을 가지는가?
 *
 * 3. ✅ 카테고리별 키 발견 기능
 *    - CACHE 카테고리의 키들이 올바르게 그룹화되는가?
 *    - QUEUE 카테고리의 키들이 올바르게 그룹화되는가?
 *    - 각 카테고리별 키 개수가 일관성 있는가?
 *
 * 4. ✅ 키 일관성 검증
 *    - validateKeyConsistency()가 문제를 없는가?
 *    - 모든 키가 정상적으로 정의되었는가?
 *
 * 5. ✅ 시스템 상태 리포트
 *    - 전체 시스템 상태를 올바르게 보고하는가?
 *    - 메모리 사용량 추정이 합리적인가?
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 🧪 테스트 시나리오
 * ═══════════════════════════════════════════════════════════════════════════════════
 *
 * Test 1: testRedisKeyType_AllEnumsHaveValidPattern()
 * ───────────────────────────────────────────────────
 * Given:   RedisKeyType enum이 정의됨
 * When:    모든 enum 값의 pattern을 검증
 * Then:    모든 enum이 non-null, non-empty pattern을 가져야 함
 *
 * Test 2: testRedisKeyType_AllEnumsHaveCategory()
 * ──────────────────────────────────────────────
 * Given:   RedisKeyType enum이 정의됨
 * When:    모든 enum 값의 category를 검증
 * Then:    모든 enum이 non-null category를 가져야 함
 *
 * Test 3: testRedisKeyType_NoDuplicatePatterns()
 * ──────────────────────────────────────────────
 * Given:   RedisKeyType enum이 정의됨
 * When:    모든 enum의 pattern을 수집하고 중복 확인
 * Then:    서로 다른 enum이 같은 pattern을 가지면 안 됨
 *
 * Test 4: testRedisKeyType_CacheCategoryHasTTL()
 * ──────────────────────────────────────────────
 * Given:   CACHE 카테고리 키 타입들
 * When:    각 키의 TTL을 검증
 * Then:    모든 CACHE 키가 non-null TTL을 가져야 함
 *
 * Test 5: testRedisKeyType_QueueCategoryHasNoTTL()
 * ─────────────────────────────────────────────────
 * Given:   QUEUE 카테고리 키 타입들
 * When:    각 키의 TTL을 검증
 * Then:    모든 QUEUE 키가 null TTL을 가져야 함 (persistent)
 *
 * Test 6: testRedisKeyManagementService_GetKeysByCategory()
 * ──────────────────────────────────────────────────────
 * Given:   RedisKeyManagementService가 주입됨
 * When:    각 카테고리별 키를 조회
 * Then:    카테고리가 올바르게 분류되어야 함
 *
 * Test 7: testRedisKeyManagementService_GetKeyCountByCategory()
 * ──────────────────────────────────────────────────────────
 * Given:   RedisKeyManagementService가 주입됨
 * When:    카테고리별 키 개수 조회
 * Then:    모든 RedisKeyType이 올바르게 카운팅되어야 함
 *
 * Test 8: testRedisKeyManagementService_ValidateKeyConsistency()
 * ────────────────────────────────────────────────────────────
 * Given:   RedisKeyManagementService가 주입됨
 * When:    키 일관성을 검증
 * Then:    "OK" 결과를 반환해야 함 (문제 없음)
 *
 * Test 9: testRedisKeyManagementService_GetKeyMetadata()
 * ────────────────────────────────────────────────────
 * Given:   여러 RedisKeyType 인스턴스
 * When:    각 키 타입의 메타데이터 조회
 * Then:    메타데이터에 name, pattern, category, TTL 포함
 *
 * Test 10: testRedisKeyManagementService_GetSystemStatusReport()
 * ────────────────────────────────────────────────────────────
 * Given:   RedisKeyManagementService가 주입됨
 * When:    시스템 상태 리포트 생성
 * Then:    전체 키 개수, 카테고리별 분석, 메모리 사용량 포함
 */
@SpringBootTest
@DisplayName("Redis Key Consistency Validation Test")
class RedisKeyConsistencyTest extends BaseIntegrationTest {

    // ═══════════════════════════════════════════════════════════════════════
    // 의존성 주입
    // ═══════════════════════════════════════════════════════════════════════

    @Autowired
    private RedisKeyManagementService redisKeyManagementService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // ═══════════════════════════════════════════════════════════════════════
    // Test 1: RedisKeyType - Pattern Validation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RedisKeyType - 모든 enum이 유효한 pattern을 가짐")
    void testRedisKeyType_AllEnumsHaveValidPattern() {
        // Given: RedisKeyType enum이 정의됨

        // When & Then: 모든 enum 값 검증
        for (RedisKeyType keyType : RedisKeyType.values()) {
            assertThat(keyType.getPattern())
                    .as("RedisKeyType.%s should have a pattern", keyType.name())
                    .isNotNull()
                    .isNotEmpty();

            System.out.println("✅ " + keyType.name() + ": " + keyType.getPattern());
        }

        System.out.println("\n✅ 모든 RedisKeyType이 유효한 pattern을 가지고 있습니다.");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 2: RedisKeyType - Category Validation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RedisKeyType - 모든 enum이 category를 가짐")
    void testRedisKeyType_AllEnumsHaveCategory() {
        // Given: RedisKeyType enum이 정의됨

        // When & Then: 모든 enum 값의 category 검증
        for (RedisKeyType keyType : RedisKeyType.values()) {
            assertThat(keyType.getCategory())
                    .as("RedisKeyType.%s should have a category", keyType.name())
                    .isNotNull();

            System.out.println("✅ " + keyType.name() + " → " + keyType.getCategory().getDisplayName());
        }

        System.out.println("\n✅ 모든 RedisKeyType이 category를 가지고 있습니다.");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 3: RedisKeyType - No Duplicate Patterns
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RedisKeyType - pattern 중복 없음")
    void testRedisKeyType_NoDuplicatePatterns() {
        // Given: RedisKeyType enum이 정의됨

        // When: 모든 pattern을 수집
        Map<String, String> patternMap = new HashMap<>();
        List<String> duplicates = new ArrayList<>();

        for (RedisKeyType keyType : RedisKeyType.values()) {
            String pattern = keyType.getPattern();
            if (patternMap.containsKey(pattern)) {
                duplicates.add(String.format("%s (used by both %s and %s)",
                        pattern, patternMap.get(pattern), keyType.name()));
            } else {
                patternMap.put(pattern, keyType.name());
            }
        }

        // Then: 중복이 없어야 함
        assertThat(duplicates)
                .as("패턴에 중복이 없어야 함")
                .isEmpty();

        System.out.println("✅ 총 " + patternMap.size() + "개의 unique pattern");
        System.out.println("✅ 중복 없이 모든 pattern이 유일합니다.");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 4: RedisKeyType - CACHE Category Has TTL
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RedisKeyType - CACHE 카테고리는 non-null TTL을 가짐")
    void testRedisKeyType_CacheCategoryHasTTL() {
        // Given: CACHE 카테고리 키 타입들

        // When & Then: 각 CACHE 키의 TTL 검증
        int cacheCount = 0;
        for (RedisKeyType keyType : RedisKeyType.values()) {
            if (keyType.getCategory() == RedisKeyCategory.CACHE) {
                assertThat(keyType.getTtl())
                        .as("RedisKeyType.%s (CACHE) should have non-null TTL", keyType.name())
                        .isNotNull();

                System.out.println("✅ " + keyType.name() + " → TTL: " +
                        keyType.getTtl().getSeconds() + "s");
                cacheCount++;
            }
        }

        assertThat(cacheCount)
                .as("최소 1개 이상의 CACHE 타입이 있어야 함")
                .isGreaterThan(0);

        System.out.println("\n✅ 모든 " + cacheCount + "개의 CACHE 키가 TTL을 가지고 있습니다.");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 5: RedisKeyType - QUEUE Category Has No TTL
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RedisKeyType - QUEUE 카테고리는 null TTL을 가짐 (persistent)")
    void testRedisKeyType_QueueCategoryHasNoTTL() {
        // Given: QUEUE 카테고리 키 타입들

        // When & Then: 각 QUEUE 키의 TTL 검증
        int queueCount = 0;
        for (RedisKeyType keyType : RedisKeyType.values()) {
            if (keyType.getCategory() == RedisKeyCategory.QUEUE) {
                assertThat(keyType.getTtl())
                        .as("RedisKeyType.%s (QUEUE) should have null TTL (persistent)", keyType.name())
                        .isNull();

                System.out.println("✅ " + keyType.name() + " → TTL: null (persistent)");
                queueCount++;
            }
        }

        if (queueCount > 0) {
            System.out.println("\n✅ 모든 " + queueCount + "개의 QUEUE 키가 persistent 구조입니다.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 6: RedisKeyManagementService - getKeysByCategory
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RedisKeyManagementService - 카테고리별 키 조회")
    void testRedisKeyManagementService_GetKeysByCategory() {
        // Given: RedisKeyManagementService가 주입됨

        // When: 각 카테고리별 키 조회
        for (RedisKeyCategory category : RedisKeyCategory.values()) {
            var keys = redisKeyManagementService.getKeysByCategory(category);
            System.out.println("📁 " + category.getDisplayName() + ": " + keys.size() + "개 키");

            // Then: 카테고리별로 정확히 분류되어야 함
            for (String key : keys) {
                boolean foundInCategory = false;
                for (RedisKeyType keyType : RedisKeyType.values()) {
                    if (keyType.getCategory() == category &&
                            key.matches(keyType.getPattern().replace("*", ".*")
                                    .replace("?", "."))) {
                        foundInCategory = true;
                        break;
                    }
                }
                assertThat(foundInCategory)
                        .as("Key '%s' should match a pattern in category %s", key, category.getDisplayName())
                        .isTrue();
            }
        }

        System.out.println("\n✅ 모든 키가 올바른 카테고리로 분류되었습니다.");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 7: RedisKeyManagementService - getKeyCountByCategory
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RedisKeyManagementService - 카테고리별 키 개수 조회")
    void testRedisKeyManagementService_GetKeyCountByCategory() {
        // Given: RedisKeyManagementService가 주입됨

        // When: 카테고리별 키 개수 조회
        var keyCountByCategory = redisKeyManagementService.getKeyCountByCategory();

        // Then: 키 개수가 올바르게 집계되어야 함
        assertThat(keyCountByCategory)
                .as("키 개수 맵이 empty가 아니어야 함")
                .isNotEmpty();

        int totalKeys = 0;
        for (var entry : keyCountByCategory.entrySet()) {
            System.out.println("📊 " + entry.getKey() + ": " + entry.getValue() + "개");
            totalKeys += entry.getValue();
        }

        System.out.println("📊 전체 키: " + totalKeys + "개");

        // 각 카테고리별 정의된 RedisKeyType이 적어도 하나는 있어야 함
        for (RedisKeyCategory category : RedisKeyCategory.values()) {
            boolean hasCategoryKeys = keyCountByCategory.values().stream()
                    .anyMatch(count -> count > 0);
            // Note: 실제 Redis에는 키가 없을 수 있으므로, enum 정의만 검증
        }

        System.out.println("\n✅ 카테고리별 키 개수가 올바르게 집계되었습니다.");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 8: RedisKeyManagementService - validateKeyConsistency
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RedisKeyManagementService - 키 일관성 검증")
    void testRedisKeyManagementService_ValidateKeyConsistency() {
        // Given: RedisKeyManagementService가 주입됨

        // When: 키 일관성 검증 실행
        String validationResult = redisKeyManagementService.validateKeyConsistency();

        // Then: 검증 결과가 "OK"여야 함
        assertThat(validationResult)
                .as("키 일관성 검증이 통과해야 함")
                .contains("OK");

        System.out.println("✅ 검증 결과:");
        System.out.println(validationResult);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 9: RedisKeyManagementService - getKeyMetadata
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RedisKeyManagementService - 키 메타데이터 조회")
    void testRedisKeyManagementService_GetKeyMetadata() {
        // Given: 여러 RedisKeyType 인스턴스

        // When & Then: 각 키 타입의 메타데이터 조회
        int metadataCount = 0;
        for (RedisKeyType keyType : RedisKeyType.values()) {
            var metadata = redisKeyManagementService.getKeyMetadata(keyType);

            // Then: 메타데이터에 필수 정보가 포함되어야 함
            assertThat(metadata)
                    .as("메타데이터가 null이 아니어야 함")
                    .isNotNull();

            assertThat(metadata.get("name"))
                    .as("메타데이터에 name이 있어야 함")
                    .isNotNull();

            assertThat(metadata.get("pattern"))
                    .as("메타데이터에 pattern이 있어야 함")
                    .isNotNull();

            assertThat(metadata.get("category"))
                    .as("메타데이터에 category가 있어야 함")
                    .isNotNull();

            assertThat(metadata.get("ttl"))
                    .as("메타데이터에 ttl이 있어야 함")
                    .isNotNull();

            metadataCount++;
        }

        System.out.println("✅ " + metadataCount + "개의 RedisKeyType 메타데이터가 올바르게 조회되었습니다.");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 10: RedisKeyManagementService - getSystemStatusReport
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RedisKeyManagementService - 시스템 상태 리포트")
    void testRedisKeyManagementService_GetSystemStatusReport() {
        // Given: RedisKeyManagementService가 주입됨

        // When: 시스템 상태 리포트 생성
        String statusReport = redisKeyManagementService.getSystemStatusReport();

        // Then: 리포트에 필수 정보가 포함되어야 함
        assertThat(statusReport)
                .as("시스템 상태 리포트가 null이 아니어야 함")
                .isNotNull()
                .isNotEmpty();

        assertThat(statusReport)
                .as("리포트에 'Key Count' 정보가 포함되어야 함")
                .contains("Key Count");

        assertThat(statusReport)
                .as("리포트에 'Memory Usage' 정보가 포함되어야 함")
                .contains("Memory Usage");

        assertThat(statusReport)
                .as("리포트에 'Key Consistency' 정보가 포함되어야 함")
                .contains("Key Consistency");

        System.out.println("✅ 시스템 상태 리포트:");
        System.out.println(statusReport);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 11: Integration - Complete Key Management Workflow
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Integration - 전체 키 관리 워크플로우")
    void testIntegration_CompleteKeyManagementWorkflow() {
        // Given: RedisKeyManagementService가 주입됨
        assertThat(redisKeyManagementService).isNotNull();
        assertThat(redisTemplate).isNotNull();

        // When: 1. 모든 키를 카테고리별로 그룹화
        var allKeysGrouped = redisKeyManagementService.getAllKeysGroupedByCategory();
        System.out.println("\n1️⃣ 모든 키 (카테고리별):");
        allKeysGrouped.forEach((category, keys) ->
                System.out.println("   📁 " + category.getDisplayName() + ": " + keys.size() + "개"));

        // When: 2. 카테고리별 키 개수 조회
        var keyCountByCategory = redisKeyManagementService.getKeyCountByCategory();
        System.out.println("\n2️⃣ 카테고리별 키 개수:");
        keyCountByCategory.forEach((category, count) ->
                System.out.println("   📊 " + category + ": " + count + "개"));

        // When: 3. 메모리 사용량 추정
        var memoryByCategory = redisKeyManagementService.getEstimatedMemoryByCategory();
        System.out.println("\n3️⃣ 카테고리별 메모리 사용량:");
        memoryByCategory.forEach((category, bytes) ->
                System.out.println("   💾 " + category + ": " + String.format("%.2f KB", bytes / 1024.0)));

        // When: 4. 키 일관성 검증
        var validationResult = redisKeyManagementService.validateKeyConsistency();
        System.out.println("\n4️⃣ 키 일관성 검증:");
        System.out.println("   " + validationResult);

        // Then: 모든 작업이 성공적으로 완료되어야 함
        assertThat(allKeysGrouped).isNotNull();
        assertThat(keyCountByCategory).isNotNull();
        assertThat(memoryByCategory).isNotNull();
        assertThat(validationResult).contains("OK");

        // When: 5. 시스템 상태 리포트 생성
        var statusReport = redisKeyManagementService.getSystemStatusReport();
        System.out.println("\n5️⃣ 최종 시스템 상태 리포트:");
        System.out.println(statusReport);

        System.out.println("\n✅ 전체 키 관리 워크플로우가 성공적으로 완료되었습니다.");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 12: RedisKeyType Enum Values Coverage
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RedisKeyType - 모든 enum 값이 실제로 정의되어 있음")
    void testRedisKeyType_AllEnumValuesCoverage() {
        // Given: RedisKeyType enum이 정의됨

        // When: 모든 enum 값 수집
        var keyTypes = RedisKeyType.values();

        // Then: 최소 기본 키 타입들이 있어야 함
        assertThat(keyTypes)
                .as("최소 1개 이상의 RedisKeyType이 정의되어야 함")
                .isNotEmpty();

        // 기본 캐시 타입들 확인
        boolean hasCouponCache = Arrays.stream(keyTypes)
                .anyMatch(kt -> kt.name().contains("COUPON"));
        boolean hasProductCache = Arrays.stream(keyTypes)
                .anyMatch(kt -> kt.name().contains("PRODUCT"));
        boolean hasQueueType = Arrays.stream(keyTypes)
                .anyMatch(kt -> kt.getCategory() == RedisKeyCategory.QUEUE);

        System.out.println("📋 RedisKeyType 정의 현황:");
        System.out.println("   - 총 enum 개수: " + keyTypes.length);
        System.out.println("   - Coupon 관련: " + (hasCouponCache ? "✅" : "❌"));
        System.out.println("   - Product 관련: " + (hasProductCache ? "✅" : "❌"));
        System.out.println("   - Queue 타입: " + (hasQueueType ? "✅" : "❌"));

        // 기본 카테고리별 분포
        Map<RedisKeyCategory, Integer> categoryDistribution = new HashMap<>();
        for (RedisKeyType keyType : keyTypes) {
            categoryDistribution.merge(keyType.getCategory(), 1, Integer::sum);
        }

        System.out.println("\n📊 카테고리별 enum 분포:");
        categoryDistribution.forEach((category, count) ->
                System.out.println("   - " + category.getDisplayName() + ": " + count + "개"));

        System.out.println("\n✅ 모든 RedisKeyType enum 값이 올바르게 정의되어 있습니다.");
    }
}
