package com.hhplus.ecommerce.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 기본통합테스트 - TestContainers 기반 (MySQL + Redis)
 *
 * 모든 통합 테스트(데이터베이스 + 캐시 기반)가 상속해야 하는 기본 클래스입니다.
 * TestContainers를 사용하여 MySQL과 Redis 컨테이너를 자동으로 관리합니다.
 *
 * 사용 방법:
 * ```java
 * public class 나의통합테스트 extends 기본통합테스트 {
 *     @Autowired
 *     private 어떤저장소 저장소;
 *
 *     @Autowired
 *     private RedisTemplate 레디스템플릿;
 *
 *     @Test
 *     public void 무언가검증한다() {
 *         // 실제 데이터베이스 및 캐시 테스트
 *     }
 * }
 * ```
 *
 * 특징:
 * - TestContainers MySQL 자동 시작 (동적 포트 할당)
 * - TestContainers Redis 자동 시작 (동적 포트 할당)
 * - 각 테스트 메서드 후 자동 롤백 (@Transactional)
 * - 데이터베이스 스키마 자동 생성/제거 (create-drop)
 * - 테스트 간 완벽한 격리 (MySQL + Redis 독립 환경)
 * - 별도의 bash 스크립트나 수동 설치 필요 없음 (Docker만 필요)
 *
 * 동시성 제어:
 * - Redis 분산락 (@DistributedLock) 테스트 지원
 * - DB 비관적 락 (SELECT...FOR UPDATE) 테스트 지원
 * - 캐시 일관성 검증 테스트 지원
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
@Testcontainers
@ContextConfiguration(initializers = BaseIntegrationTest.TestContainersInitializer.class)
public abstract class BaseIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ecommerce_test")
            .withUsername("testuser")
            .withPassword("testpass")
            .withEnv("MYSQL_ROOT_PASSWORD", "testroot");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.0")
            .withExposedPorts(6379);

    /**
     * TestContainers 동적 포트를 Spring 설정에 전달하는 Initializer
     *
     * MySQL과 Redis 모두의 동적 포트를 Spring 환경변수에 주입합니다.
     * 포트 충돌을 방지하고 테스트 격리를 보장합니다.
     */
    static class TestContainersInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            // === MySQL 설정 ===
            applicationContext.getEnvironment().getSystemProperties().put(
                    "spring.datasource.url",
                    mysql.getJdbcUrl()
            );
            applicationContext.getEnvironment().getSystemProperties().put(
                    "spring.datasource.username",
                    mysql.getUsername()
            );
            applicationContext.getEnvironment().getSystemProperties().put(
                    "spring.datasource.password",
                    mysql.getPassword()
            );

            // === Redis 설정 ===
            // Redis 호스트와 포트를 동적으로 설정
            applicationContext.getEnvironment().getSystemProperties().put(
                    "spring.redis.host",
                    redis.getHost()
            );
            applicationContext.getEnvironment().getSystemProperties().put(
                    "spring.redis.port",
                    String.valueOf(redis.getMappedPort(6379))
            );
        }
    }
}