package com.hhplus.ecommerce.unit.infrastructure.config;

import com.hhplus.ecommerce.infrastructure.config.RedisKeyCategory;
import com.hhplus.ecommerce.infrastructure.config.RedisKeyManagementService;
import com.hhplus.ecommerce.infrastructure.config.RedisKeyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * RedisKeyManagementService 단위 테스트
 *
 * 검증 항목:
 * - getKeyMetadata(): MEMORY USAGE 실측값이 actual_memory_bytes로 저장
 * - getKeyDetails(): MEMORY USAGE 실측값이 actual_memory_bytes로 저장
 * - fetchMemoryUsage가 null 반환 시 fallback 0L
 * - Redis execute 예외 발생 시 fallback 0L
 * - 키 없을 때 actual_memory_bytes 미포함
 *
 * 의도적으로 opsForValue().size()를 사용하지 않음을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("[Unit] RedisKeyManagementService - MEMORY USAGE 실측 조회")
class RedisKeyManagementServiceTest {

    @Mock
    @SuppressWarnings("rawtypes")
    private RedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    private RedisKeyManagementService service;

    private static final RedisKeyType TEST_KEY_TYPE = RedisKeyType.CACHE_COUPON_LIST;
    private static final String SAMPLE_KEY = "cache:coupon:list";
    private static final long EXPECTED_BYTES = 1024L;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new RedisKeyManagementService(redisTemplate);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getKeyMetadata()
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("getKeyMetadata()")
    class GetKeyMetadata {

        @Test
        @DisplayName("키가 존재하고 MEMORY USAGE가 정상 반환되면 actual_memory_bytes에 실측값 저장")
        @SuppressWarnings("unchecked")
        void returnsActualMemoryBytes_whenKeyExists() {
            given(redisTemplate.keys(anyString())).willReturn(Set.of(SAMPLE_KEY));
            given(redisTemplate.execute(any(RedisCallback.class))).willReturn(EXPECTED_BYTES);

            Map<String, Object> metadata = service.getKeyMetadata(TEST_KEY_TYPE);

            assertThat(metadata).containsKey("actual_memory_bytes");
            assertThat(metadata.get("actual_memory_bytes")).isEqualTo(EXPECTED_BYTES);
        }

        @Test
        @DisplayName("MEMORY USAGE가 null 반환(키 미존재) 시 actual_memory_bytes = 0")
        @SuppressWarnings("unchecked")
        void fallbackToZero_whenMemoryUsageReturnsNull() {
            given(redisTemplate.keys(anyString())).willReturn(Set.of(SAMPLE_KEY));
            given(redisTemplate.execute(any(RedisCallback.class))).willReturn(null);

            Map<String, Object> metadata = service.getKeyMetadata(TEST_KEY_TYPE);

            assertThat(metadata.get("actual_memory_bytes")).isEqualTo(0L);
        }

        @Test
        @DisplayName("Redis execute 예외 발생 시 actual_memory_bytes = 0 (서비스 중단 없음)")
        @SuppressWarnings("unchecked")
        void fallbackToZero_whenExecuteThrows() {
            given(redisTemplate.keys(anyString())).willReturn(Set.of(SAMPLE_KEY));
            given(redisTemplate.execute(any(RedisCallback.class)))
                    .willThrow(new RuntimeException("Redis 연결 실패"));

            Map<String, Object> metadata = service.getKeyMetadata(TEST_KEY_TYPE);

            assertThat(metadata.get("actual_memory_bytes")).isEqualTo(0L);
        }

        @Test
        @DisplayName("키가 없으면 actual_memory_bytes 항목 자체가 존재하지 않음")
        @SuppressWarnings("unchecked")
        void noMemoryBytesKey_whenNoKeysExist() {
            given(redisTemplate.keys(anyString())).willReturn(Set.of());

            Map<String, Object> metadata = service.getKeyMetadata(TEST_KEY_TYPE);

            assertThat(metadata).doesNotContainKey("actual_memory_bytes");
        }

        @Test
        @DisplayName("opsForValue().size()를 호출하지 않는다 — MEMORY USAGE 방식만 사용")
        @SuppressWarnings("unchecked")
        void doesNotUseOpsForValueSize() {
            given(redisTemplate.keys(anyString())).willReturn(Set.of(SAMPLE_KEY));
            given(redisTemplate.execute(any(RedisCallback.class))).willReturn(EXPECTED_BYTES);

            service.getKeyMetadata(TEST_KEY_TYPE);

            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("메타데이터에 name, description, pattern, category, ttl, actual_count 포함")
        @SuppressWarnings("unchecked")
        void containsAllRequiredFields() {
            given(redisTemplate.keys(anyString())).willReturn(Set.of(SAMPLE_KEY));
            given(redisTemplate.execute(any(RedisCallback.class))).willReturn(EXPECTED_BYTES);

            Map<String, Object> metadata = service.getKeyMetadata(TEST_KEY_TYPE);

            assertThat(metadata).containsKeys("name", "description", "pattern", "category", "ttl", "actual_count");
            assertThat(metadata.get("actual_count")).isEqualTo(1);
        }

        @Test
        @DisplayName("estimated_memory_bytes 키가 더 이상 사용되지 않는다 (구 키 이름 제거 검증)")
        @SuppressWarnings("unchecked")
        void doesNotContainEstimatedMemoryBytes() {
            given(redisTemplate.keys(anyString())).willReturn(Set.of(SAMPLE_KEY));
            given(redisTemplate.execute(any(RedisCallback.class))).willReturn(EXPECTED_BYTES);

            Map<String, Object> metadata = service.getKeyMetadata(TEST_KEY_TYPE);

            assertThat(metadata).doesNotContainKey("estimated_memory_bytes");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getKeyDetails()
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("getKeyDetails()")
    class GetKeyDetails {

        @Test
        @DisplayName("MEMORY USAGE가 정상 반환되면 actual_memory_bytes에 실측값 저장")
        @SuppressWarnings("unchecked")
        void returnsActualMemoryBytes_whenKeyExists() {
            given(redisTemplate.getExpire(SAMPLE_KEY)).willReturn(300L);
            given(redisTemplate.type(SAMPLE_KEY))
                    .willReturn(org.springframework.data.redis.connection.DataType.STRING);
            given(redisTemplate.execute(any(RedisCallback.class))).willReturn(EXPECTED_BYTES);

            Map<String, Object> details = service.getKeyDetails(SAMPLE_KEY);

            assertThat(details.get("actual_memory_bytes")).isEqualTo(EXPECTED_BYTES);
        }

        @Test
        @DisplayName("MEMORY USAGE가 null 반환 시 actual_memory_bytes = 0")
        @SuppressWarnings("unchecked")
        void fallbackToZero_whenMemoryUsageReturnsNull() {
            given(redisTemplate.getExpire(SAMPLE_KEY)).willReturn(300L);
            given(redisTemplate.type(SAMPLE_KEY))
                    .willReturn(org.springframework.data.redis.connection.DataType.STRING);
            given(redisTemplate.execute(any(RedisCallback.class))).willReturn(null);

            Map<String, Object> details = service.getKeyDetails(SAMPLE_KEY);

            assertThat(details.get("actual_memory_bytes")).isEqualTo(0L);
        }

        @Test
        @DisplayName("Redis execute 예외 발생 시 actual_memory_bytes = 0 (서비스 중단 없음)")
        @SuppressWarnings("unchecked")
        void fallbackToZero_whenExecuteThrows() {
            given(redisTemplate.getExpire(SAMPLE_KEY)).willReturn(300L);
            given(redisTemplate.type(SAMPLE_KEY))
                    .willReturn(org.springframework.data.redis.connection.DataType.STRING);
            given(redisTemplate.execute(any(RedisCallback.class)))
                    .willThrow(new RuntimeException("Redis 연결 실패"));

            Map<String, Object> details = service.getKeyDetails(SAMPLE_KEY);

            assertThat(details.get("actual_memory_bytes")).isEqualTo(0L);
        }

        @Test
        @DisplayName("TTL이 음수(만료 없음)이면 ttl_seconds = 'no expiration'")
        @SuppressWarnings("unchecked")
        void noExpiration_whenTtlIsNegative() {
            given(redisTemplate.getExpire(SAMPLE_KEY)).willReturn(-1L);
            given(redisTemplate.type(SAMPLE_KEY))
                    .willReturn(org.springframework.data.redis.connection.DataType.STRING);
            given(redisTemplate.execute(any(RedisCallback.class))).willReturn(EXPECTED_BYTES);

            Map<String, Object> details = service.getKeyDetails(SAMPLE_KEY);

            assertThat(details.get("ttl_seconds")).isEqualTo("no expiration");
        }

        @Test
        @DisplayName("opsForValue().size()를 호출하지 않는다 — MEMORY USAGE 방식만 사용")
        @SuppressWarnings("unchecked")
        void doesNotUseOpsForValueSize() {
            given(redisTemplate.getExpire(SAMPLE_KEY)).willReturn(300L);
            given(redisTemplate.type(SAMPLE_KEY))
                    .willReturn(org.springframework.data.redis.connection.DataType.STRING);
            given(redisTemplate.execute(any(RedisCallback.class))).willReturn(EXPECTED_BYTES);

            service.getKeyDetails(SAMPLE_KEY);

            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("memory_bytes 키가 더 이상 사용되지 않는다 (구 키 이름 제거 검증)")
        @SuppressWarnings("unchecked")
        void doesNotContainOldMemoryBytesKey() {
            given(redisTemplate.getExpire(SAMPLE_KEY)).willReturn(300L);
            given(redisTemplate.type(SAMPLE_KEY))
                    .willReturn(org.springframework.data.redis.connection.DataType.STRING);
            given(redisTemplate.execute(any(RedisCallback.class))).willReturn(EXPECTED_BYTES);

            Map<String, Object> details = service.getKeyDetails(SAMPLE_KEY);

            assertThat(details).doesNotContainKey("memory_bytes")
                               .doesNotContainKey("estimated_memory_bytes");
        }

        @Test
        @DisplayName("key, ttl_seconds, type, actual_memory_bytes 모두 포함")
        @SuppressWarnings("unchecked")
        void containsAllRequiredFields() {
            given(redisTemplate.getExpire(SAMPLE_KEY)).willReturn(300L);
            given(redisTemplate.type(SAMPLE_KEY))
                    .willReturn(org.springframework.data.redis.connection.DataType.STRING);
            given(redisTemplate.execute(any(RedisCallback.class))).willReturn(EXPECTED_BYTES);

            Map<String, Object> details = service.getKeyDetails(SAMPLE_KEY);

            assertThat(details).containsKeys("key", "ttl_seconds", "type", "actual_memory_bytes");
            assertThat(details.get("key")).isEqualTo(SAMPLE_KEY);
        }
    }
}
