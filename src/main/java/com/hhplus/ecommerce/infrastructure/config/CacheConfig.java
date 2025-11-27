package com.hhplus.ecommerce.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 캐시 설정 (Phase 2: Redis 기반 분산 캐싱)
 *
 * RedisCacheManager를 사용하여 Redis에서 캐시를 관리합니다.
 * 각 캐시별로 다른 TTL을 설정하여 성능 최적화를 달성합니다.
 *
 * 캐시 전략:
 * - productList: 상품 목록 조회 (TTL: 1시간, 빈도: 매우 높음)
 * - couponList: 쿠폰 목록 조회 (TTL: 30분, 빈도: 높음)
 * - productDetail: 상품 상세 조회 (TTL: 2시간, 빈도: 높음)
 * - cartItems: 장바구니 아이템 (TTL: 30분, 빈도: 중간)
 * - popularProducts: 인기 상품 조회 (TTL: 1시간, 빈도: 높음)
 *
 * 예상 효과:
 * - Product 목록 조회: TPS 200 → 1000 (5배)
 * - Coupon 목록 조회: TPS 300 → 2000 (6배)
 * - 응답시간 87% 감소
 * - 서버 인스턴스 간 캐시 공유 가능
 * - 캐시 무효화 일관성 보장
 */
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

    private final RedisConnectionFactory redisConnectionFactory;

    /**
     * ObjectMapper 설정 (JSON 직렬화/역직렬화)
     *
     * Redis에 저장되는 모든 객체는 JSON으로 직렬화되므로,
     * ObjectMapper를 통해 타입 정보를 포함하여 역직렬화할 수 있도록 설정합니다.
     */
    @Bean
    public ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    /**
     * RedisTemplate 설정 (Redis 일반 작업용)
     *
     * Key: String 직렬화
     * Value: Jackson JSON 직렬화
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // Key 직렬화
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Value 직렬화 (Jackson)
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer =
                new Jackson2JsonRedisSerializer<>(cacheObjectMapper(), Object.class);

        // 설정 적용
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 캐시 매니저 설정 (RedisCacheManager)
     *
     * 캐시별 TTL 설정:
     * - productList: 1시간
     * - couponList: 30분
     * - productDetail: 2시간
     * - cartItems: 30분
     *
     * @CacheEvict로 캐시 무효화 시 Redis에서도 즉시 제거됩니다.
     */
    @Bean
    public CacheManager cacheManager() {
        // 기본 캐시 설정 (TTL: 10분)
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()
                        )
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new Jackson2JsonRedisSerializer<>(Object.class)
                        )
                )
                .disableCachingNullValues();  // null 값 캐시 금지

        // 캐시별 개별 설정
        Map<String, RedisCacheConfiguration> cacheConfigMap = new HashMap<>();

        // productList: 1시간
        cacheConfigMap.put("productList", defaultConfig
                .entryTtl(Duration.ofHours(1)));

        // couponList: 30분
        cacheConfigMap.put("couponList", defaultConfig
                .entryTtl(Duration.ofMinutes(30)));

        // productDetail: 2시간
        cacheConfigMap.put("productDetail", defaultConfig
                .entryTtl(Duration.ofHours(2)));

        // cartItems: 30분
        cacheConfigMap.put("cartItems", defaultConfig
                .entryTtl(Duration.ofMinutes(30)));

        // popularProducts: 1시간 (인기 상품, 최근 3일 주문 수량 기준)
        cacheConfigMap.put("popularProducts", defaultConfig
                .entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigMap)
                .build();
    }
}
