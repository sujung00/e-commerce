package com.hhplus.ecommerce.config;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.lifecycle.Startables;

/**
 * Spring Boot 애플리케이션 컨텍스트 초기화 클래스
 *
 * TestContainers를 사용하여 MySQL 컨테이너를 시작하고
 * Spring 환경변수에 연결 정보를 자동으로 설정합니다.
 *
 * 사용 방법:
 * @SpringBootTest(initializers = TestContainersInitializer.class)
 * public class MyIntegrationTest {
 *     // 테스트 코드
 * }
 */
public class TestContainersInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final MySQLContainer<?> MYSQL_CONTAINER;

    static {
        MYSQL_CONTAINER = new MySQLContainer<>("mysql:8.0.35")
                .withDatabaseName("ecommerce_test")
                .withUsername("root")
                .withPassword("root")
                .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_unicode_ci",
                    "--max_connections=1000",
                    "--sql-mode=''",
                    "--default-storage-engine=InnoDB"
                );

        // 컨테이너 시작
        Startables.deepStart(MYSQL_CONTAINER).join();
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        TestPropertyValues.of(
                "spring.datasource.url=" + MYSQL_CONTAINER.getJdbcUrl(),
                "spring.datasource.username=" + MYSQL_CONTAINER.getUsername(),
                "spring.datasource.password=" + MYSQL_CONTAINER.getPassword(),
                "spring.datasource.driver-class-name=" + MYSQL_CONTAINER.getDriverClassName(),
                "spring.jpa.hibernate.ddl-auto=create-drop"
        ).applyTo(applicationContext.getEnvironment());
    }

    public static MySQLContainer<?> getMysqlContainer() {
        return MYSQL_CONTAINER;
    }
}
