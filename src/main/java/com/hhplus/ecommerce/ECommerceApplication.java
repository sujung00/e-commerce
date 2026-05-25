package com.hhplus.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * E-Commerce 애플리케이션 메인 클래스
 *
 * 활성화된 기능:
 * - @EnableAsync: 비동기 메서드 실행 지원
 * - @EnableRetry: Spring Retry를 통한 재시도 메커니즘 지원
 * - @EnableAspectJAutoProxy: AOP Aspect 자동 프록시 생성
 * - @EnableCaching: Redis 캐시 AOP 인프라 조기 등록
 *   (CacheConfig 가 BeanPostProcessor 초기화 시 일찍 생성되므로
 *    @EnableCaching 을 메인 클래스에서 별도 활성화하여 AOP 프록시 보장)
 * - @EnableScheduling: @Scheduled 어노테이션 활성화
 *   (없으면 CouponQueueService.processCouponQueue() 워커가 실행되지 않음!
 *    OutboxPollingService.pollAndPublishOutbox() 도 동일하게 비활성화됨)
 *   ※ 2026-05-25 Consumer TPS 측정 중 발견 — 해당 어노테이션 누락 버그 수정
 */
@EnableAsync
@EnableRetry
@EnableAspectJAutoProxy
@EnableCaching
@EnableScheduling
@SpringBootApplication
public class ECommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ECommerceApplication.class, args);
    }

}
