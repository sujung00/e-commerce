package com.hhplus.ecommerce.infrastructure.config.web;

import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * AppConfig - API 전역 설정
 * 모든 요청에 /api prefix를 자동으로 추가합니다.
 *
 * 방법: PathMatchConfigurer를 사용하여 모든 컨트롤러 요청에 /api prefix 추가
 */
@Configuration
public class AppConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 모든 @RestController와 @Controller 매핑에 /api prefix 추가
        configurer.addPathPrefix("/api", c -> c.isAnnotationPresent(
                org.springframework.web.bind.annotation.RestController.class) ||
                c.isAnnotationPresent(org.springframework.stereotype.Controller.class));
    }
}
