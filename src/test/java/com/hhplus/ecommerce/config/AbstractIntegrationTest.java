package com.hhplus.ecommerce.config;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통합 테스트 기본 클래스
 *
 * 모든 통합 테스트가 상속해야 하는 기본 클래스입니다.
 * TestContainers MySQL 환경을 자동으로 구성하고,
 * 트랜잭션 격리를 통해 테스트 간 데이터 격리를 보장합니다.
 *
 * 사용 방법:
 * ```java
 * @SpringBootTest
 * public class MyIntegrationTest extends AbstractIntegrationTest {
 *     @Test
 *     public void testSomething() {
 *         // 테스트 코드
 *     }
 * }
 * ```
 *
 * 기능:
 * - TestContainers MySQL 자동 시작 (동적 포트 할당)
 * - 각 테스트 메서드 후 자동 롤백 (@Transactional)
 * - 데이터베이스 스키마 자동 생성/제거 (create-drop)
 * - 테스트 간 완벽한 격리
 */
@SpringBootTest
@Transactional
@ContextConfiguration(initializers = TestContainersInitializer.class)
public abstract class AbstractIntegrationTest {
    // 공통 설정이 적용되는 기본 클래스
}
