package com.hhplus.ecommerce.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 통합 테스트 기본 클래스 (TestContainers 기반)
 *
 * 모든 통합 테스트(Database 기반)가 상속해야 하는 기본 클래스입니다.
 * TestContainers를 사용하여 MySQL 컨테이너를 자동으로 관리합니다.
 *
 * 사용 방법:
 * ```java
 * public class MyIntegrationTest extends BaseIntegrationTest {
 *     @Autowired
 *     private SomeRepository repository;
 *
 *     @Test
 *     public void testSomething() {
 *         // 실제 데이터베이스 테스트
 *     }
 * }
 * ```
 *
 * 특징:
 * - TestContainers MySQL 자동 시작 (동적 포트 할당)
 * - 각 테스트 메서드 후 자동 롤백 (@Transactional)
 * - 데이터베이스 스키마 자동 생성/제거 (create-drop)
 * - 테스트 간 완벽한 격리
 * - 별도의 bash 스크립트나 설치 필요 없음 (Docker만 필요)
 */
@SpringBootTest
@Transactional
@Testcontainers
@ContextConfiguration(initializers = BaseIntegrationTest.TestContainersInitializer.class)
public abstract class BaseIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ecommerce_test")
            .withUsername("root")
            .withPassword("root");

    /**
     * TestContainers 동적 포트를 Spring 설정에 전달하는 Initializer
     */
    static class TestContainersInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
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
        }
    }
}