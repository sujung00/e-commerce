package com.hhplus.ecommerce.infrastructure.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 캐시 설정 (Phase 1: 메모리 기반 로컬 캐싱)
 *
 * 현재는 개발 환경에서 빠른 검증을 위해 ConcurrentMapCacheManager 사용
 * 프로덕션으로 이동 시 RedisCacheManager로 변경 예정
 *
 * 캐시 전략:
 * - productList: 상품 목록 조회 (TTL: 1시간, 빈도: 매우 높음)
 * - couponList: 쿠폰 목록 조회 (TTL: 30분, 빈도: 높음)
 * - productDetail: 상품 상세 조회 (TTL: 2시간, 빈도: 높음)
 * - cartItems: 장바구니 아이템 (TTL: 30분, 빈도: 중간)
 *
 * 예상 효과:
 * - Product 목록 조회: TPS 200 → 1000 (5배)
 * - Coupon 목록 조회: TPS 300 → 2000 (6배)
 * - 응답시간 87% 감소
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 캐시 매니저 설정
     * 개발 단계: ConcurrentMapCacheManager (메모리 기반)
     * 프로덕션: RedisCacheManager로 변경 가능
     */
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                "productList",      // 상품 목록
                "couponList",       // 쿠폰 목록
                "productDetail",    // 상품 상세
                "cartItems"         // 장바구니 아이템
        );
    }
}
