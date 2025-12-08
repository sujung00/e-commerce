package com.hhplus.ecommerce.infrastructure.config.database;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * P6Spy 설정 클래스
 *
 * test 프로필에서만 활성화되며, SQL 쿼리 로깅을 위한 P6Spy 설정을 제공합니다.
 * - 커스텀 MessageFormattingStrategy 등록
 * - 멀티라인 SQL 로깅
 * - 바인딩된 인자와 함께 완성된 SQL 출력
 */
@Configuration
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "test")
public class P6SpyConfig {

    /**
     * P6Spy MessageFormattingStrategy Bean 등록
     *
     * P6Spy가 SQL 로그를 포매팅할 때 사용할 커스텀 전략을 제공합니다.
     * 바인딩된 인자가 포함된 완성된 SQL을 보기 좋은 형식으로 출력합니다.
     *
     * @return P6SpyPrettySqlFormatter 인스턴스
     */
    @Bean
    public MessageFormattingStrategy p6SpyMessageFormattingStrategy() {
        return new P6SpyPrettySqlFormatter();
    }
}
