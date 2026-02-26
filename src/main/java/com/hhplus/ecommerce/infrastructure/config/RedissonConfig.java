package com.hhplus.ecommerce.infrastructure.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 설정 (분산 락 및 고급 Redis 기능)
 *
 * Redisson은 spring.data.redis 설정을 자동으로 사용하지 않으므로,
 * application.yml의 spring.data.redis 값을 읽어 RedissonClient Bean을 직접 구성합니다.
 *
 * 용도:
 * - 분산 락 (선착순 쿠폰 발급, 재고 차감 등)
 * - 분산 컬렉션
 * - Pub/Sub
 *
 * Docker 환경:
 * - Redis 컨테이너: redis:6379 (docker-compose 서비스명)
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Value("${spring.data.redis.database}")
    private int database;

    @Value("${spring.data.redis.timeout}")
    private String timeout;

    /**
     * RedissonClient Bean 생성
     *
     * Single Server 모드로 Redis에 연결
     * - Address: redis://[host]:[port]
     * - Database: application.yml에서 지정한 DB 인덱스
     * - Timeout: 연결 및 응답 타임아웃
     *
     * @return RedissonClient
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setTimeout(parseTimeout(timeout))
                .setConnectionPoolSize(20)
                .setConnectionMinimumIdleSize(5);

        return Redisson.create(config);
    }

    /**
     * timeout 문자열 파싱 (2000ms → 2000)
     */
    private int parseTimeout(String timeout) {
        return Integer.parseInt(timeout.replace("ms", ""));
    }
}