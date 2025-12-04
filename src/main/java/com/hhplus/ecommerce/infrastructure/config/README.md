# Infrastructure Configuration Guide

## 📁 Folder Structure

```
infrastructure/config/
├── cache/           - Redis 캐싱 설정
├── lock/            - 분산락 (Redisson) 설정
├── database/        - 데이터베이스 프로파일 설정 (P6Spy)
├── aspect/          - AOP & 인터셉터 설정 (Spring Retry)
└── web/             - REST API 설정 (PathPrefix)
```

---

## 📦 각 폴더 설명

### 1. `cache/` - Redis 캐싱 설정

**파일**: `CacheConfig.java`

**역할**:
- RedisCacheManager 빈 등록
- 각 캐시별 TTL 설정
- Jackson 직렬화 설정

**사용 기능**:
- @Cacheable, @CacheEvict 어노테이션
- 자동 캐시 무효화
- 캐시 전략별 TTL 관리

**예제**:
```java
@Cacheable(value = PRODUCT_LIST_CACHE, key = PRODUCT_LIST_KEY)
public ProductListResponse getProductList(int page, int size, String sort) {
    // ...
}
```

---

### 2. `lock/` - 분산락 (Redisson) 설정

**파일**: `RedissonConfig.java`

**역할**:
- RedissonClient 빈 등록
- Redis 연결 풀 설정
- 재시도 및 타임아웃 설정

**사용 기능**:
- @DistributedLock 어노테이션
- 분산락 관리 (락 획득/해제)
- 동시성 제어

**설정값**:
- Connection pool size: 10
- Connect timeout: 2000ms
- Retry attempts: 3
- Retry interval: 1500ms

**예제**:
```java
@DistributedLock(
    key = "#orderId",
    waitTime = 5,
    leaseTime = 2
)
public void processOrder(Long orderId) {
    // 동시에 한 건만 실행됨
}
```

---

### 3. `database/` - 데이터베이스 프로파일 설정

**파일**:
- `P6SpyConfig.java`
- `P6SpyPrettySqlFormatter.java`

**역할**:
- SQL 쿼리 로깅 (개발 환경)
- SQL 포매팅 및 보기좋은 출력
- 바인딩된 인자 포함 출력

**활성화 조건**:
- `spring.profiles.active=test` 또는 `dev`

**로그 예시**:
```sql
-- Before
select u.id, u.name from users u where u.id = ?

-- After (P6Spy)
select u.id, u.name
from users u
where u.id = 1
```

---

### 4. `aspect/` - AOP & 인터셉터 설정

**파일**: `RetryConfig.java`

**역할**:
- @EnableRetry 활성화
- Spring Retry AOP 프록시 설정
- 메서드 레벨 재시도 로직

**사용 기능**:
- @Retryable 어노테이션
- @Recover 복구 메서드
- Exponential backoff + Jitter

**예제**:
```java
@Retryable(
    maxAttempts = 3,
    backoff = @Backoff(delay = 50, multiplier = 2)
)
public void orderPayment(Order order) {
    // 실패시 최대 3회 재시도
}

@Recover
public void orderPaymentRecover(OptimisticLockException e, Order order) {
    // 재시도 실패시 호출
}
```

---

### 5. `web/` - REST API 설정

**파일**: `AppConfig.java`

**역할**:
- 모든 컨트롤러에 `/api` prefix 추가
- REST API 경로 표준화
- PathMatchConfigurer 설정

**효과**:
```
Before: localhost:8080/products
After:  localhost:8080/api/products
```

**대상**:
- @RestController 클래스
- @Controller 클래스

---

## 🔧 설정 로딩 순서

Spring이 설정 클래스를 자동으로 발견하고 로드하는 순서:

1. **Application Start** → Spring Boot main()
2. **Component Scan** → infrastructure.config 패키지 검사
3. **각 @Configuration 클래스 로드**:
   - RetryConfig: Spring Retry AOP 활성화
   - AppConfig: PathPrefix 등록
   - CacheConfig: RedisCacheManager 빈 등록
   - RedissonConfig: RedissonClient 빈 등록
   - P6SpyConfig: P6Spy 설정 (@ConditionalOnProperty)

---

## 📊 설정 의존성

```
RetryConfig (독립적)
    ↓
AppConfig (웹 설정)

CacheConfig (Redis)
    ↓
RedissonConfig (Redis)

P6SpyConfig (데이터베이스 - 조건부)
    ├─ P6SpyPrettySqlFormatter (내부)
```

---

## ✅ 설정 검증

### 설정이 올바르게 로드되었는지 확인

```bash
# 로그에서 확인
2024-11-01 10:00:00.000  INFO  ... RetryConfig : @EnableRetry activated
2024-11-01 10:00:00.100  INFO  ... AppConfig : /api prefix added
2024-11-01 10:00:00.200  INFO  ... CacheConfig : RedisCacheManager initialized
2024-11-01 10:00:00.300  INFO  ... RedissonConfig : RedissonClient initialized
```

### 기능 확인

- **캐싱**: Redis에 데이터 저장/조회 ✓
- **분산락**: 동시성 제어 정상 작동 ✓
- **REST API**: `/api/*` 경로 정상 작동 ✓
- **SQL 로깅**: SQL 쿼리가 보기좋게 출력됨 ✓

---

## 🚀 추가 설정 시 가이드라인

새로운 설정을 추가할 때:

1. **목적에 맞는 폴더 선택**:
   - 캐싱 관련 → `cache/`
   - 락/동시성 → `lock/`
   - 데이터베이스 → `database/`
   - AOP/Aspect → `aspect/`
   - REST API → `web/`
   - 기타 → 새 폴더 생성

2. **패키지명 설정**:
   ```java
   package com.hhplus.ecommerce.infrastructure.config.{category};
   ```

3. **@Configuration 어노테이션 추가**:
   ```java
   @Configuration
   public class MyConfig {
       // ...
   }
   ```

4. **문서화**:
   - 클래스 JavaDoc 추가
   - 설정값의 의미 설명
   - 사용 예제 제공

---

## 📚 참고 자료

- [Spring Boot Configuration Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/configuration-metadata.html)
- [Redis Configuration](https://docs.spring.io/spring-data/redis/docs/current/reference/html/)
- [Spring Retry](https://github.com/spring-projects/spring-retry)
- [P6Spy Documentation](https://p6spy.readthedocs.io/)

