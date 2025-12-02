package com.hhplus.ecommerce.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 분산락 설정
 *
 * RedissonClient를 스프링 빈으로 등록하여
 * 애플리케이션 전체에서 분산락을 사용할 수 있도록 제공합니다.
 */
@Configuration
@RequiredArgsConstructor
public class RedissonConfig {

    @Value("${spring.redis.host}")
    private String host;

    @Value("${spring.redis.port}")
    private int port;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(10)
                .setConnectTimeout(2000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        return Redisson.create(config);
    }
}
