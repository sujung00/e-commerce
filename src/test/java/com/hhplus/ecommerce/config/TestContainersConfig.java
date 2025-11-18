package com.hhplus.ecommerce.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * TestContainers 설정 클래스
 *
 * MySQL 컨테이너를 자동으로 시작하고 Spring Boot와 연결하는 설정을 제공합니다.
 *
 * 특징:
 * - 테스트 실행 시 MySQL 컨테이너 자동 시작
 * - 여러 테스트 클래스가 같은 컨테이너를 공유하여 성능 향상
 * - 테스트 완료 후 자동 정리
 *
 * 사용 방법:
 * 1. @SpringBootTest 클래스에 @Import(TestContainersConfig.class) 추가
 * 2. 또는 IntegrationTest 기본 클래스를 상속받기
 */
@TestConfiguration
public class TestContainersConfig {

    private static final String MYSQL_IMAGE = "mysql:8.0.35";
    private static final String DATABASE_NAME = "ecommerce_test";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "root";

    /**
     * MySQL 컨테이너 빈 정의
     *
     * 특징:
     * - 싱글톤으로 관리되어 여러 테스트가 같은 인스턴스 사용
     * - 컨테이너가 시작되면 자동으로 Spring 환경변수 설정
     * - 애플리케이션 종료 시 자동으로 컨테이너 정지
     */
    @Bean
    public MySQLContainer<?> mysqlContainer() {
        MySQLContainer<?> container = new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
                .withDatabaseName(DATABASE_NAME)
                .withUsername(USERNAME)
                .withPassword(PASSWORD)
                .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_unicode_ci",
                    "--max_connections=1000",
                    "--sql-mode=''",
                    "--default-storage-engine=InnoDB"
                );

        // 컨테이너 시작
        container.start();

        return container;
    }
}
