# P6Spy 테스트 환경 프로필 설정 가이드

## 개요

P6Spy를 **테스트 환경에서만 활성화**하고, 로컬(개발) 및 운영 환경에서는 기본 MySQL DataSource를 사용하도록 구성했습니다.

---

## 1. P6Spy란?

P6Spy는 Java 기반 SQL 모니터링 및 성능 분석 도구입니다.

### 기능

```
P6Spy가 하는 일:
┌─────────────────────────────────────┐
│  Application (JPA/Hibernate)        │
└──────────────┬──────────────────────┘
               │
       [SQL 쿼리 전송]
               │
┌──────────────▼──────────────────────┐
│  P6Spy Proxy Layer                  │
│ ├─ SQL 쿼리 가로채기                  │
│ ├─ 파라미터 바인딩 값 표출           │
│ ├─ 쿼리 실행 시간 측정              │
│ └─ 로그 출력 (SLF4J)                 │
└──────────────┬──────────────────────┘
               │
       [실제 SQL 실행]
               │
┌──────────────▼──────────────────────┐
│  MySQL Database                     │
└─────────────────────────────────────┘
```

### 장점

- ✅ 실제 실행되는 SQL 쿼리 확인
- ✅ 파라미터 바인딩 값 표출 (`?` 대신 실제 값 표시)
- ✅ 쿼리 실행 시간 측정
- ✅ N+1 쿼리 감지
- ✅ 성능 분석 및 최적화

---

## 2. 프로파일별 구성 (Profile-based Configuration)

### 2.1 프로파일 구조

```
├── test 프로필
│   ├─ application-test.yml
│   │  └─ driver-class-name: com.p6spy.engine.spy.P6SpyDriver
│   ├─ TestDataSourceConfiguration.java (@Profile("test"))
│   │  └─ P6DataSource로 래핑
│   ├─ spy.properties
│   │  └─ P6Spy 상세 설정
│   └─ logging 설정
│      ├─ p6spy: DEBUG
│      └─ org.hibernate.SQL: DEBUG
│
├── dev 프로필
│   ├─ application-dev.yml
│   │  └─ driver-class-name: com.mysql.cj.jdbc.Driver
│   ├─ logging 설정
│   │  └─ org.hibernate.SQL: DEBUG (P6Spy 없음)
│   └─ 기본 MySQL DataSource
│
└── prod 프로필
    ├─ application-prod.yml
    │  └─ driver-class-name: com.mysql.cj.jdbc.Driver
    ├─ logging 설정
    │  └─ root: WARN (P6Spy 없음)
    └─ 기본 MySQL DataSource (최적화됨)
```

### 2.2 프로파일 실행 방법

#### Test 프로필 (P6Spy 활성화)
```bash
# Maven
mvn clean test

# Gradle
./gradlew test

# 또는 명시적으로
java -jar app.jar --spring.profiles.active=test
```

#### Dev 프로필 (개발 환경, P6Spy 비활성화)
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

#### Prod 프로필 (운영 환경, P6Spy 비활성화)
```bash
./gradlew bootRun --args='--spring.profiles.active=prod'
```

---

## 3. 설정 파일 상세 분석

### 3.1 application-test.yml (테스트 환경)

```yaml
spring:
  datasource:
    # P6Spy 드라이버 사용
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver

    # P6Spy 설정
    p6spy:
      spylogmodulename: com.p6spy.engine.logging.P6LogFactory

  jpa:
    # 테스트: 모든 변경사항 show-sql로 표출
    show-sql: true
    properties:
      hibernate:
        format_sql: true

logging:
  level:
    p6spy: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

**특징**:
- P6Spy 드라이버 활성화
- SQL 로깅 상세 모드
- 파라미터 바인딩 값 표출

### 3.2 application-dev.yml (개발 환경)

```yaml
spring:
  datasource:
    # 기본 MySQL JDBC 드라이버 (P6Spy 없음)
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true

logging:
  level:
    org.hibernate.SQL: DEBUG
```

**특징**:
- 기본 MySQL DataSource
- Hibernate SQL 로깅만 사용
- P6Spy 오버헤드 없음

### 3.3 application-prod.yml (운영 환경)

```yaml
spring:
  datasource:
    # 기본 MySQL JDBC 드라이버 (P6Spy 없음)
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 30  # 더 큰 풀

  jpa:
    show-sql: false  # 성능 최적화
    properties:
      hibernate:
        format_sql: false
        jdbc:
          batch_size: 50  # 더 큰 배치

logging:
  level:
    root: WARN  # 최소 로깅
```

**특징**:
- 기본 MySQL DataSource (빠름)
- SQL 로깅 비활성화
- 최대 성능 최적화

---

## 4. TestDataSourceConfiguration.java (핵심 구현)

```java
@Configuration
@Profile("test")  // 테스트 프로필에서만 활성화
public class TestDataSourceConfiguration {

    @Bean
    public DataSource dataSource() {
        // 1. P6Spy 초기화
        P6SpyOptions.getActiveInstance()
            .setLogMessageFormat(
                "# executionTime=%executionTime ms | " +
                "statement=%statement | " +
                "bindvars=%bindvars"
            );

        // 2. HikariCP 설정 (기본 MySQL 연결)
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/ecommerce_test...");
        config.setUsername("root");
        config.setPassword("Happy0904*");
        config.setMaximumPoolSize(5);
        // ... 기타 HikariCP 설정

        // 3. 기본 DataSource 생성
        HikariDataSource basicDataSource = new HikariDataSource(config);

        // 4. P6Spy로 래핑 (모든 SQL 가로채기)
        return new P6DataSource(basicDataSource);
    }
}
```

**동작 원리**:
1. `@Profile("test")` → 테스트 실행 시에만 이 설정이 로드됨
2. `P6DataSource` → 실제 DataSource를 래핑하여 SQL 가로채기
3. 로그 출력 → 모든 SQL 쿼리가 콘솔/로그 파일에 기록됨

---

## 5. spy.properties (P6Spy 상세 설정)

```properties
# 로그 메시지 포맷
logMessageFormat=%(currentTime)|%(executionTime)|%(category)|connection%(connectionId)|%(sqlSingleLine)

# 애플리케이션 로그에 포함
appender=com.p6spy.engine.logging.appenders.Slf4JLogger

# 실행 시간 단위
dateformat=yyyy-MM-dd'T'HH:mm:ss.SSS
```

**로그 출력 예시**:
```
2025-11-12T10:30:45.123|15|statement|connection1|SELECT * FROM users WHERE user_id = 1
2025-11-12T10:30:45.145|8|statement|connection1|SELECT * FROM products
```

---

## 6. 로그 출력 비교

### Test 프로필 (P6Spy + Hibernate 로깅)

```
# P6Spy 로그
2025-11-12 10:30:45.123 | 15ms | SELECT * FROM users WHERE user_id = 1

# Hibernate 로그
Hibernate: SELECT u1_0.user_id,u1_0.username,u1_0.balance FROM users u1_0 WHERE u1_0.user_id=?
2025-11-12T10:30:45.100 | userId=1

# 결과: 파라미터 값 확인 + P6Spy 성능 분석
```

### Dev 프로필 (Hibernate 로깅만)

```
# Hibernate 로그만 표출
Hibernate: SELECT u1_0.user_id,u1_0.username,u1_0.balance FROM users u1_0 WHERE u1_0.user_id=?
2025-11-12T10:30:45.100 | userId=1

# P6Spy 없음: 추가 오버헤드 없음
```

### Prod 프로필 (최소 로깅)

```
# 로그 거의 없음 (성능 최우선)
# WARN 레벨 이상만 표출 (에러만 기록)

# 결과: 최고 성능
```

---

## 7. 의존성 설정 (build.gradle)

```gradle
dependencies {
    // P6Spy: 테스트에서만 사용
    testImplementation 'com.github.gavlyukovskiy:p6spy-spring-boot-starter:1.9.1'
}
```

**주의사항**:
- `testImplementation` (프로덕션에 포함되지 않음)
- P6Spy는 개발/테스트 도구이므로 운영 환경에 포함되면 안 됨

---

## 8. 프로파일별 테스트

### 8.1 Test 프로필 실행

```bash
# 테스트 실행 (자동으로 application-test.yml 로드)
./gradlew test

# 콘솔 출력 예시:
# [P6Spy] 2025-11-12 10:30:45.123 | 15ms | SELECT * FROM users...
# [Hibernate] Hibernate: SELECT u1_0.user_id,...
```

### 8.2 Dev 프로필 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'

# 콘솔 출력 예시:
# [Hibernate] Hibernate: SELECT u1_0.user_id,...
# (P6Spy 로그 없음)
```

### 8.3 Prod 프로필 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=prod'

# 콘솔 출력 예시:
# (거의 로그 없음, WARN 이상만 표출)
```

---

## 9. Spring 프로파일 우선순위

### 로드 순서

```
1. application.yml (기본 설정)
2. application-{profile}.yml (프로필 오버라이드)
3. @Configuration @Profile("...") (프로그래밍 설정)

예: test 프로필 실행 시
   application.yml
        ↓
   application-test.yml (덮어쓰기)
        ↓
   @Profile("test") @Configuration 활성화
```

### 프로파일 명시 방법

```bash
# 1. 명령행 인자
java -jar app.jar --spring.profiles.active=test

# 2. 환경 변수
export SPRING_PROFILES_ACTIVE=test
java -jar app.jar

# 3. application.yml
spring:
  profiles:
    active: test

# 4. IDE 설정 (IntelliJ)
Run → Edit Configurations → VM options
-Dspring.profiles.active=test
```

---

## 10. 장점 요약

| 항목 | Test | Dev | Prod |
|------|------|-----|------|
| **SQL 로깅** | P6Spy + Hibernate | Hibernate only | 최소 |
| **파라미터 표출** | ✅ | ✅ | ✅ |
| **성능** | 중간 (모니터링) | 빠름 | 매우 빠름 |
| **용도** | 디버깅, 성능 분석 | 개발 | 운영 |
| **오버헤드** | ~20% | ~5% | 최소 |

---

## 11. P6Spy 로그 분석 팁

### 느린 쿼리 감지

```
# 15ms 이상 걸린 쿼리 확인
2025-11-12 10:30:45.123 | 1523ms | SELECT * FROM orders o INNER JOIN order_items oi...
                                 ↑
                            너무 느림! 인덱스 필요
```

### N+1 쿼리 감지

```
# 같은 쿼리가 여러 번 실행
2025-11-12 10:30:45.100 | 5ms | SELECT * FROM products WHERE product_id = 1
2025-11-12 10:30:45.106 | 4ms | SELECT * FROM products WHERE product_id = 2
2025-11-12 10:30:45.112 | 3ms | SELECT * FROM products WHERE product_id = 3
                         ↑
                    반복! Eager Loading 필요
```

### 파라미터 확인

```
# P6Spy는 실제 값을 표출
# P6Spy: ... WHERE user_id = 1 AND status = 'ACTIVE'
# Hibernate: ... WHERE user_id = ? AND status = ?
```

---

## 12. 문제 해결

### 문제: P6Spy가 활성화되지 않음

**원인**: `@Profile("test")` 프로필이 로드되지 않음

**해결**:
```bash
# 프로필 명시 확인
./gradlew test -Dspring.profiles.active=test

# 또는 IDE에서 VM 옵션 확인
-Dspring.profiles.active=test
```

### 문제: P6Spy 로그가 너무 많음

**원인**: 로그 레벨이 DEBUG

**해결**:
```yaml
# application-test.yml에서 로그 레벨 조정
logging:
  level:
    p6spy: INFO  # DEBUG → INFO로 변경
```

### 문제: Dev 환경에서 P6Spy가 활성화됨

**원인**: P6Spy 드라이버가 application.yml에 설정됨

**해결**:
```yaml
# application.yml → 테스트용이므로 삭제
# application-dev.yml만 사용
driver-class-name: com.mysql.cj.jdbc.Driver  # P6Spy 제거
```

---

## 13. 참고 자료

### P6Spy 공식 문서
- GitHub: https://github.com/p6spy/p6spy
- 설정 옵션: https://p6spy.readthedocs.io/en/latest/configandusage.html

### Spring 프로파일
- Spring Docs: https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.profiles

### SQL 로깅 비교
```
┌─────────────┬───────────────────┬────────────────┐
│ 로거        │ 파라미터 표출      │ 성능 오버헤드   │
├─────────────┼───────────────────┼────────────────┤
│ Hibernate   │ ✅                 │ ~5%            │
│ P6Spy       │ ✅ (상세)          │ ~15-20%        │
│ log4j2      │ ❌                 │ ~2%            │
└─────────────┴───────────────────┴────────────────┘
```

---

## 14. 결론

✅ **테스트 환경**: P6Spy로 완전한 SQL 모니터링
✅ **개발 환경**: Hibernate 로깅으로 충분한 디버깅
✅ **운영 환경**: 최소 로깅으로 최고 성능

프로파일 분리를 통해 각 환경의 요구사항에 최적화된 설정을 제공합니다!

---

**마지막 업데이트**: 2025-11-12
**작성자**: Claude Code
**대상**: 개발팀
