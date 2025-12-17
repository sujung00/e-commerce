package com.hhplus.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * E-Commerce 애플리케이션 메인 클래스
 *
 * 활성화된 기능:
 * - @EnableAsync: 비동기 메서드 실행 지원
 * - @EnableRetry: Spring Retry를 통한 재시도 메커니즘 지원
 * - @EnableAspectJAutoProxy: AOP Aspect 자동 프록시 생성
 */
@EnableAsync
@EnableRetry
@EnableAspectJAutoProxy
@SpringBootApplication
public class ECommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ECommerceApplication.class, args);
    }

}
