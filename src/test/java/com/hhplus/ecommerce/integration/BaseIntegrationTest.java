package com.hhplus.ecommerce.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

/**
 * 기본통합테스트 - TestContainers 기반 (MySQL + Redis)
 *
 * 모든 통합 테스트(데이터베이스 + 캐시 기반)가 상속해야 하는 기본 클래스입니다.
 * TestContainers를 사용하여 MySQL과 Redis 컨테이너를 자동으로 관리합니다.
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 📋 사용 방법
 * ═══════════════════════════════════════════════════════════════════════════════════
 * ```java
 * @DisplayName("Product Service Redis 캐시 테스트")
 * class ProductServiceCacheTest extends BaseIntegrationTest {
 *     @Autowired
 *     private ProductService productService;
 *
 *     @Autowired
 *     private ProductRepository productRepository;
 *
 *     @Autowired
 *     private CacheManager cacheManager;  // RedisCacheManager 주입
 *
 *     @Autowired
 *     private RedisTemplate<String, Object> redisTemplate;  // Redis 직접 접근
 *
 *     @Test
 *     void 캐시_저장_및_조회_테스트() {
 *         // Given: 상품 데이터 준비
 *         Product product = productRepository.save(...);
 *
 *         // When: 첫 호출 (DB 조회)
 *         ProductDetailResponse result1 = productService.getProductDetail(product.getId());
 *
 *         // Then: Redis에 캐시 저장됨
 *         String cacheKey = "cache:productDetail::" + product.getId();
 *         Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
 *         assertThat(cachedValue).isNotNull();  // ✅ Redis 히트 검증
 *     }
 * }
 * ```
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * ✨ 특징
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 1. ✅ RedisCacheManager 자동 사용 (application-test.yml의 spring.cache.type=redis)
 * 2. ✅ TestContainers MySQL 자동 시작 (동적 포트 할당)
 * 3. ✅ TestContainers Redis 자동 시작 (동적 포트 할당)
 * 4. ✅ 각 테스트 메서드 후 자동 롤백 (@Transactional)
 * 5. ✅ 데이터베이스 스키마 자동 생성/제거 (create-drop)
 * 6. ✅ 테스트 간 완벽한 격리 (MySQL + Redis 독립 환경)
 * 7. ✅ 별도의 bash 스크립트나 수동 설치 필요 없음 (Docker만 필요)
 * 8. ✅ Redis 캐시 실제 검증 (RedisTemplate을 통한 직접 조회 가능)
 * 9. ✅ 분산락 테스트 지원 (@DistributedLock)
 * 10. ✅ 캐시 일관성 검증 가능
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 🔧 동작 원리
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 1. TestContainers가 Docker를 사용하여 MySQL 8.0과 Redis 7.0 컨테이너 시작
 * 2. MySQL: 동적 포트 할당 (예: 32769)
 * 3. Redis: 동적 포트 할당 (예: 32770)
 * 4. TestContainersInitializer가 다음을 Spring 환경변수에 주입:
 *    - spring.datasource.url: jdbc:mysql://localhost:32769/ecommerce_test
 *    - spring.datasource.username: testuser
 *    - spring.datasource.password: testpass
 *    - spring.redis.host: 172.17.0.2 (또는 localhost)
 *    - spring.redis.port: 32770
 * 5. application-test.yml의 spring.cache.type=redis로 RedisCacheManager 활성화
 * 6. CacheConfig.java의 RedisCacheManager가 동적 Redis 연결 사용
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * ⚠️ Docker 연결 문제 해결
 * ═══════════════════════════════════════════════════════════════════════════════════
 * macOS에서 다음과 같은 오류가 발생하는 경우:
 * "Could not find a valid Docker environment"
 *
 * 해결 방법:
 *
 * 2. Docker Desktop이 실행 중인지 확인
 * 3. 터미널에서 `docker ps` 실행 가능 여부 확인
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 🧪 테스트 실행
 * ═══════════════════════════════════════════════════════════════════════════════════
 * ```bash
 * # 전체 통합 테스트 실행
 * ./gradlew testIntegration
 *
 * # 특정 테스트만 실행
 * ./gradlew test --tests "ProductServiceCacheTest"
 *
 * # Redis 연결 로그 확인 (디버그 모드)
 * ./gradlew testIntegration --info 2>&1 | grep -i redis
 * ```
 *
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 🎯 캐시 테스트 검증 시나리오
 * ═══════════════════════════════════════════════════════════════════════════════════
 * 1. @Cacheable 작동 확인
 *    - 첫 호출: DB에서 조회 (느림)
 *    - 두 번째 호출: Redis에서 조회 (빠름)
 *    - 응답시간 비교로 캐시 효과 검증
 *
 * 2. Redis 캐시 데이터 직접 확인
 *    - RedisTemplate으로 캐시 키/값 조회
 *    - TTL 검증 (getExpire 사용)
 *
 * 3. @CacheEvict 작동 확인
 *    - 캐시 무효화 후 Redis에서 삭제 확인
 *
 * 4. 성능 개선 측정
 *    - 캐시 미스: 87-100ms (DB 쿼리)
 *    - 캐시 히트: 5-15ms (Redis 조회)
 *    - 약 5~10배 성능 향상 기대
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
@ContextConfiguration(initializers = BaseIntegrationTest.TestContainersInitializer.class)
public abstract class BaseIntegrationTest {

    // ═══════════════════════════════════════════════════════════════════════
    // TestContainers 컨테이너 정의 (Singleton Container Pattern)
    // ═══════════════════════════════════════════════════════════════════════
    // ⚠️ @Testcontainers + @Container 를 사용하지 않는다.
    //    이유: 추상 부모 클래스에 @Testcontainers가 붙으면 각 구체 서브클래스마다
    //    컨테이너가 stop/start를 반복한다. 그러면 포트가 바뀌어도 Spring Test Context
    //    캐시는 이전 포트로 연결하려 하므로 HikariPool total=0 → 전체 테스트 실패.
    //
    //    Singleton Container Pattern: static 초기화 블록에서 직접 start() 호출.
    //    컨테이너는 JVM 종료 시까지 한 번만 실행되며, 모든 테스트 클래스가 공유한다.

    /**
     * MySQL 8.0 TestContainer (Singleton)
     */
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ecommerce_test")
            .withUsername("testuser")
            .withPassword("testpass")
            .withEnv("MYSQL_ROOT_PASSWORD", "testroot");

    /**
     * Redis 7.0 TestContainer (Singleton)
     */
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.0")
            .withExposedPorts(6379);

    // 컨테이너를 JVM 시작 시 한 번만 기동 (Singleton Container Pattern)
    static {
        mysql.start();
        redis.start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TestContainers Initializer
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * TestContainers 동적 포트를 Spring 설정에 전달하는 Initializer
     *
     * MySQL과 Redis의 동적 포트를 Spring 환경변수에 주입합니다.
     * 이를 통해 포트 충돌을 방지하고 테스트 격리를 보장합니다.
     *
     * 주입되는 속성:
     * - spring.datasource.url: jdbc:mysql://localhost:{RANDOM_PORT}/ecommerce_test
     * - spring.datasource.username: testuser
     * - spring.datasource.password: testpass
     * - spring.redis.host: Docker 내부 IP 또는 localhost
     * - spring.redis.port: {RANDOM_PORT}
     * - spring.cache.type: redis (application-test.yml에서 설정)
     *
     * 이 설정이 적용되면:
     * 1. CacheConfig의 RedisCacheManager가 자동으로 생성됨
     * 2. @Cacheable 어노테이션이 Redis와 동작
     * 3. RedisTemplate이 TestContainers Redis와 연결됨
     */
    static class TestContainersInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            // ═════════════════════════════════════════════════════════════
            // 1️⃣ MySQL 설정 - application.yml의 datasource 설정 덮어씌움
            // ═════════════════════════════════════════════════════════════
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

            // ═════════════════════════════════════════════════════════════
            // 1.5️⃣ HikariCP 풀 크기 - 동시성 테스트를 위한 충분한 커넥션 확보
            // 50 스레드 × REQUIRES_NEW 중첩 = 최대 100 커넥션 필요 → 120으로 설정
            // ═════════════════════════════════════════════════════════════
            applicationContext.getEnvironment().getSystemProperties().put(
                    "spring.datasource.hikari.maximum-pool-size",
                    "120"
            );
            applicationContext.getEnvironment().getSystemProperties().put(
                    "spring.datasource.hikari.connection-timeout",
                    "60000"
            );

            // ═════════════════════════════════════════════════════════════
            // 2️⃣ Redis 설정 - application-test.yml의 spring.redis 설정 덮어씌움
            // ═════════════════════════════════════════════════════════════
            // Redis 호스트와 포트를 동적으로 설정
            String redisHost = redis.getHost();
            Integer redisPort = redis.getMappedPort(6379);

            applicationContext.getEnvironment().getSystemProperties().put(
                    "spring.redis.host",
                    redisHost
            );
            applicationContext.getEnvironment().getSystemProperties().put(
                    "spring.redis.port",
                    String.valueOf(redisPort)
            );

            // ═════════════════════════════════════════════════════════════
            // 3️⃣ 캐시 설정 - application-test.yml에서 이미 설정됨
            // ═════════════════════════════════════════════════════════════
            // spring.cache.type=redis는 application-test.yml에서 명시
            // RedisCacheManager가 위의 spring.redis 설정을 사용하여 생성됨

            // ═════════════════════════════════════════════════════════════
            // 📊 디버그 로그
            // ═════════════════════════════════════════════════════════════
            System.out.println("\n" +
                    "╔══════════════════════════════════════════════════════════════════════╗\n" +
                    "║              🐳 TestContainers 초기화 완료                          ║\n" +
                    "╠══════════════════════════════════════════════════════════════════════╣\n" +
                    "║ 🗄️  MySQL                                                           ║\n" +
                    "║    JDBC URL: " + mysql.getJdbcUrl() + "\n" +
                    "║    Username: " + mysql.getUsername() + "\n" +
                    "║                                                                      ║\n" +
                    "║ 💾 Redis                                                            ║\n" +
                    "║    Host: " + redisHost + "\n" +
                    "║    Port: " + redisPort + "\n" +
                    "║    Cache Type: RedisCacheManager (spring.cache.type=redis)         ║\n" +
                    "║                                                                      ║\n" +
                    "║ ✅ @Cacheable / @CacheEvict는 실제 Redis에서 동작합니다             ║\n" +
                    "╚══════════════════════════════════════════════════════════════════════╝\n");
        }
    }
}