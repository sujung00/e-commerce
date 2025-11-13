# MySQL 연동 구조 및 Repository 동작 정리

## 개요

본 문서는 e-commerce 프로젝트의 MySQL 데이터베이스 연동 구조를 설명합니다.
**목표**: 새로 합류한 개발자도 쉽게 이해할 수 있는 요청-서비스-Repository-DB의 전체 흐름을 단계별로 서술합니다.

---

## 1. Repository 개요

### 1.1 계층 구조 (Hexagonal Architecture)

```
┌─────────────────────────────────────────┐
│    Controller (Presentation Layer)      │
│  요청 수신 및 응답 반환                  │
└────────────────┬────────────────────────┘
                 │ HTTP 요청/응답
┌────────────────▼────────────────────────┐
│    Service (Application Layer)          │
│  비즈니스 로직 조정 및 검증              │
└────────────────┬────────────────────────┘
                 │ Repository Interface (Port)
┌────────────────▼────────────────────────┐
│   Domain Layer - Repository Interface   │
│  추상화된 데이터 접근 인터페이스         │
└────────────────┬────────────────────────┘
                 │ 구현체 (Adapter)
┌────────────────▼────────────────────────┐
│  Infrastructure Layer - Repository Impl │
│  ├─ MySQLxxxRepository (@Primary)       │  Production용 (JPA 기반)
│  └─ InMemoryxxxRepository               │  Test/Demo용 (Map 기반)
└────────────────┬────────────────────────┘
                 │ Spring Data JPA
┌────────────────▼────────────────────────┐
│    JPA Repository Interface             │
│  (Spring Data JPA가 구현)               │
└────────────────┬────────────────────────┘
                 │ Hibernate ORM
┌────────────────▼────────────────────────┐
│    MySQL Database (localhost:3306)      │
│  hhplus_ecommerce                       │
└─────────────────────────────────────────┘
```

### 1.2 Repository 구조 요약

프로젝트에는 **5개 비즈니스 도메인**별로 Repository가 구성되어 있습니다.

| 도메인 | Port (Domain Layer) | Adapter (Infrastructure) | JPA Repository | 엔티티 |
|--------|---|---|---|---|
| **User** | `UserRepository` | `MySQLUserRepository` (Primary) | `UserJpaRepository` | `User` |
| | | `InMemoryUserRepository` | | |
| **Product** | `ProductRepository` | `MySQLProductRepository` (Primary) | `ProductJpaRepository` | `Product`, `ProductOption` |
| | | `InMemoryProductRepository` | `ProductOptionJpaRepository` | |
| **Cart** | `CartRepository` | `MySQLCartRepository` (Primary) | `CartJpaRepository` | `Cart`, `CartItem` |
| | | `InMemoryCartRepository` | `CartItemJpaRepository` | |
| **Coupon** | `CouponRepository` | `MySQLCouponRepository` (Primary) | `CouponJpaRepository` | `Coupon` |
| | `UserCouponRepository` | `MySQLUserCouponRepository` (Primary) | `UserCouponJpaRepository` | `UserCoupon` |
| | | `InMemoryCouponRepository` | | |
| | | `InMemoryUserCouponRepository` | | |
| **Order** | `OrderRepository` | `MySQLOrderRepository` (Primary) | `OrderJpaRepository` | `Order`, `OrderItem` |
| | `OutboxRepository` | `MySQLOutboxRepository` (Primary) | `OutboxJpaRepository` | `Outbox` |
| | | `InMemoryOrderRepository` | | |
| | | `InMemoryOutboxRepository` | | |

**총 23개 Repository 파일**
- JPA Repository: 8개
- MySQL Adapter: 7개
- InMemory Adapter: 8개

### 1.3 @Primary 어노테이션

MySQL 구현이 `@Primary`로 표시되어 있습니다. 이는 Spring의 빈 주입 우선순위를 정의합니다.

```java
@Repository
@Primary  // ← MySQL이 우선 선택됨
@Transactional
public class MySQLUserRepository implements UserRepository {
    private final UserJpaRepository userJpaRepository;
    // ...
}
```

**Spring 빈 주입 우선순위**:
1. `@Qualifier` 명시 → 지정된 빈 사용
2. `@Qualifier` 없음 → `@Primary` 있는 빈 사용
3. `@Primary` 없음 → NoUniqueBeanDefinitionException 발생

따라서 **프로덕션 환경에서는 자동으로 MySQL이 주입**되고, **테스트 환경에서는 TestRepositoryConfiguration의 @Primary가 작동**합니다.

---

## 2. 쿼리 메서드 동작

### 2.1 메서드 분류

#### A. 간단한 조회/저장 (Spring Data JPA 자동 생성)

```java
// UserJpaRepository
public interface UserJpaRepository extends JpaRepository<User, Long> {
    // JpaRepository에서 자동 제공:
    // - findById(Long id)           : Optional<User>
    // - findAll()                   : List<User>
    // - save(User user)             : User
    // - existsById(Long id)         : boolean
    // - delete(User user)           : void
}
```

메서드 명명 규칙:
- `findBy{Field}` → 필드로 조회
- `existsBy{Field}` → 존재 여부 확인
- `countBy{Field}` → 개수 계산
- `save()` → 저장/업데이트

**MySQLUserRepository에서의 위임**:
```java
@Override
public Optional<User> findById(Long userId) {
    return userJpaRepository.findById(userId);  // JpaRepository 메서드 위임
}
```

#### B. 커스텀 @Query 메서드 (복잡한 쿼리)

**예제 1: 페이지네이션과 정렬**
```java
// OrderJpaRepository
@Query(value = "SELECT o FROM Order o WHERE o.userId = :userId ORDER BY o.createdAt DESC LIMIT :size OFFSET :offset")
List<Order> findByUserIdWithPagination(
    @Param("userId") Long userId,
    @Param("offset") int offset,
    @Param("size") int size
);
```

**동작 흐름**:
1. `@Query`로 JPQL 작성 (SQL이 아닌 HQL)
2. `@Param` 어노테이션으로 파라미터 바인딩
3. Hibernate가 해당 DB 방언(MySQL8Dialect)으로 자동 변환
4. MySQL에 전송되어 실행

**예제 2: 비관적 락 (동시성 제어)**
```java
// CouponJpaRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM Coupon c WHERE c.couponId = :couponId")
Optional<Coupon> findByIdWithLock(@Param("couponId") Long couponId);
```

**동작**:
- `@Lock(PESSIMISTIC_WRITE)` → MySQL의 `SELECT ... FOR UPDATE` 생성
- 선착순 쿠폰 발급 시 다른 요청을 차단하여 재고 중복 발급 방지

**예제 3: 최근 3일 주문 수 조회**
```java
// ProductJpaRepository
@Query("SELECT COUNT(DISTINCT o.orderId) FROM Order o " +
       "INNER JOIN o.orderItems oi " +
       "WHERE oi.productId = :productId " +
       "AND o.createdAt >= CURRENT_TIMESTAMP - 3")
Long countOrdersInLast3Days(@Param("productId") Long productId);
```

**동작**:
1. Order와 OrderItem 엔티티를 JPQL으로 조인
2. 3일 이내 주문된 상품의 주문 수를 카운트
3. 인기 상품 조회(인기도 계산)에 사용

### 2.2 쿼리 메서드 실행 흐름

```
MySQLProductRepository.getOrderCount3Days(productId)
        │
        ├─ productJpaRepository.countOrdersInLast3Days(productId) 호출
        │
        ├─ JPA가 @Query 파싱
        │  ├─ JPQL 문자열 해석
        │  ├─ 엔티티 매핑 정보 확인 (Order → orders 테이블)
        │  └─ MySQL8Dialect로 변환
        │
        ├─ 변환된 SQL:
        │  SELECT COUNT(DISTINCT o.order_id) FROM orders o
        │  INNER JOIN order_items oi ON o.order_id = oi.order_id
        │  WHERE oi.product_id = ? AND o.created_at >= NOW() - INTERVAL 3 DAY
        │
        ├─ Hibernate가 파라미터 바인딩:
        │  ? = :productId 값 바인딩
        │
        ├─ HikariCP 커넥션 풀에서 연결 획득
        │
        ├─ MySQL 실행 및 결과 반환
        │
        └─ Long 타입으로 반환
```

---

## 3. MySQL 연동 흐름

### 3.1 MySQL 연결 설정

**파일**: `src/main/resources/application.yml`

```yaml
spring:
  datasource:
    # JDBC 연결 정보
    url: jdbc:mysql://localhost:3306/hhplus_ecommerce?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root
    password: Happy0904*

    # HikariCP 커넥션 풀 설정
    hikari:
      maximum-pool-size: 10        # 최대 동시 연결 수
      minimum-idle: 2              # 최소 유휴 연결 수
      connection-timeout: 20000    # 연결 타임아웃 (20초)
      idle-timeout: 600000         # 유휴 연결 종료 시간 (10분)
      max-lifetime: 1800000        # 최대 연결 유지 시간 (30분)

  jpa:
    database-platform: org.hibernate.dialect.MySQL8Dialect
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        format_sql: false
        jdbc:
          batch_size: 20
        order_inserts: true
        order_updates: true
```

**설정 항목 설명**:

| 항목 | 값 | 설명 |
|------|-----|------|
| **url** | `jdbc:mysql://localhost:3306/hhplus_ecommerce` | MySQL 서버 주소 및 데이터베이스명 |
| **driver-class-name** | `com.mysql.cj.jdbc.Driver` | MySQL JDBC 드라이버 |
| **maximum-pool-size** | 10 | 동시 요청 시 유지할 최대 연결 수 |
| **minimum-idle** | 2 | 유휴 시간에도 유지할 최소 연결 수 |
| **connection-timeout** | 20000ms | 풀에서 연결을 얻기 위한 대기 시간 |
| **database-platform** | MySQL8Dialect | Hibernate가 생성하는 SQL 방언 |
| **ddl-auto** | update | 시작 시 스키마 업데이트 (프로덕션: validate) |
| **batch_size** | 20 | 한 번에 처리할 INSERT/UPDATE 배치 크기 |

### 3.2 연동 계층 상세 설명

#### 3.2.1 HikariCP (커넥션 풀)

```
Spring Application
        │
        ├─ HikariCP 초기화 (application.yml 설정 읽음)
        │  ├─ 최소 2개 연결 미리 생성
        │  └─ 최대 10개까지 필요시 추가 생성
        │
        └─ 요청 시:
           ├─ 풀에서 가용 연결 획득
           ├─ 연결 사용 (쿼리 실행)
           └─ 사용 완료 후 풀에 반환
```

**이점**:
- 연결 재사용으로 DB 부하 감소
- 동시 요청 처리 효율화
- 연결 누수 방지

#### 3.2.2 JPA (객체-관계 매핑)

```
Adapter Method Call
  └─ Repository 메서드 실행
     └─ JPA가 쿼리 생성
        ├─ 엔티티 클래스 → 테이블 매핑
        ├─ 필드 → 컬럼 매핑
        └─ 관계 (@OneToMany, @ManyToOne) → 외래키 매핑

           예: Order 엔티티
           @Entity
           @Table(name = "orders")  // 테이블명
           public class Order {
               @Id
               @GeneratedValue(strategy = GenerationType.IDENTITY)
               @Column(name = "order_id")
               private Long orderId;  // order_id 컬럼

               @OneToMany(cascade = CascadeType.ALL)
               @JoinColumn(name = "order_id")
               private List<OrderItem> orderItems;  // 1:다 관계
           }
```

#### 3.2.3 Hibernate (ORM 엔진)

```
JPA 쿼리 생성
  └─ Hibernate이 JPQL/메서드명을 SQL로 변환
     ├─ 엔티티 클래스명 → 테이블명
     ├─ 필드명 → 컬럼명
     ├─ 메서드 규칙:
     │  ├─ findById(Long id) → SELECT * FROM table WHERE id = ?
     │  ├─ findByName(String name) → SELECT * FROM table WHERE name = ?
     │  └─ countByStatus(String status) → SELECT COUNT(*) FROM table WHERE status = ?
     │
     └─ @Query 어노테이션:
        └─ 개발자가 작성한 JPQL을 MySQL SQL로 변환
           예: JPQL의 CURRENT_TIMESTAMP → MySQL의 NOW()
               JPQL의 - 3 → MySQL의 INTERVAL 3 DAY
```

#### 3.2.4 EntityManager & Transaction

```
MySQLProductRepository.getOrderCount3Days(productId)
  └─ Transactional 어노테이션 활성화
     ├─ EntityManager 획득 (JPA 세션)
     ├─ 트랜잭션 시작
     │  └─ HikariCP에서 MySQL 연결 획득
     │
     ├─ 쿼리 실행 및 결과 매핑
     │  └─ ResultSet → Java 객체로 변환
     │
     └─ 트랜잭션 종료
        └─ 결과 반환 및 연결 풀에 반환
```

**@Transactional 어노테이션의 역할**:
- 모든 DB 작업을 하나의 트랜잭션으로 처리
- 작업 중 예외 발생 시 자동 롤백
- 트랜잭션 완료 후 자동 커밋

---

## 4. 요청 → 서비스 → Repository → DB 데이터 흐름

### 4.1 전체 요청 흐름 (예: 주문 생성)

```
1. HTTP 요청 (Client)
   POST /api/orders
   {
     "userId": 1,
     "items": [{"productId": 1, "quantity": 2}],
     "couponId": 100
   }

2. Controller (OrderController)
   @PostMapping("/orders")
   public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequestDto dto) {
       return ResponseEntity.ok(orderService.createOrder(...));
   }

   → OrderService로 위임

3. Service (OrderService)
   public CreateOrderResponse createOrder(CreateOrderCommand cmd) {
       // 1) 검증: OrderValidator 위임
       orderValidator.validate(cmd);

       // 2) 계산: OrderCalculator 위임
       OrderCalculator calculator = new OrderCalculator(cmd);
       Long totalAmount = calculator.calculateTotal();

       // 3) 트랜잭션: OrderTransactionService 위임
       Order savedOrder = orderTransactionService.executeOrder(cmd);

       // 4) 응답 생성
       return new CreateOrderResponse(savedOrder);
   }

   → Repository로 데이터 저장 요청

4. Repository (Infrastructure Layer)

   4.1) MySQLOrderRepository.save(Order order)
        ├─ @Transactional 어노테이션으로 트랜잭션 시작
        ├─ OrderJpaRepository.save(order) 호출
        │  └─ JPA가 엔티티를 SQL INSERT로 변환
        │     SQL: INSERT INTO orders (user_id, order_status, ...)
        │          VALUES (?, ?, ...)
        │
        └─ HikariCP 연결 획득 → MySQL 실행 → 결과 반환

   4.2) MySQLOrderRepository.save()는 또한:
        ├─ Cascade 저장 처리
        │  └─ Order의 orderItems도 함께 저장
        │     SQL: INSERT INTO order_items (order_id, product_id, ...)
        │          VALUES (?, ?, ...)
        │
        └─ @Transactional 완료
           └─ 트랜잭션 커밋 (모든 INSERT 확정)

5. Database (MySQL - hhplus_ecommerce)

   실행된 SQL:
   INSERT INTO orders (user_id, order_status, coupon_discount, subtotal, final_amount, created_at, updated_at)
   VALUES (1, 'COMPLETED', 5000, 50000, 45000, NOW(), NOW());

   INSERT INTO order_items (order_id, product_id, option_id, product_name, option_name, quantity, unit_price, subtotal, created_at)
   VALUES (1, 1, 101, '티셔츠', '블랙/M', 2, 25000, 50000, NOW());

   결과: 새로운 Order와 OrderItem이 데이터베이스에 저장됨

6. Response (서버 → 클라이언트)
   {
     "orderId": 1,
     "orderStatus": "COMPLETED",
     "items": [...],
     "finalAmount": 45000,
     "createdAt": "2025-11-12T10:30:00"
   }
```

### 4.2 읽기 작업 흐름 (예: 인기 상품 조회)

```
1. HTTP 요청 (Client)
   GET /api/products/popular

2. Controller (ProductController)
   @GetMapping("/popular")
   public ResponseEntity<?> getPopularProducts() {
       return ResponseEntity.ok(popularProductService.getTop5());
   }

3. Service (PopularProductService)
   public List<PopularProductResponse> getTop5() {
       // 1) 모든 상품 조회
       List<Product> products = productRepository.findAll();

       // 2) 각 상품의 최근 3일 주문 수 조회
       return products.stream()
           .map(product -> {
               Long orderCount = productRepository.getOrderCount3Days(product.getProductId());
               return new PopularProductResponse(product, orderCount);
           })
           .sorted(by order count DESC)
           .limit(5)
           .collect(toList());
   }

4. Repository (MySQLProductRepository)

   4.1) findAll() 호출
        SQL: SELECT * FROM products

   4.2) getOrderCount3Days(1L) 호출 (각 상품마다)
        SQL: SELECT COUNT(DISTINCT o.order_id)
             FROM orders o
             INNER JOIN order_items oi ON o.order_id = oi.order_id
             WHERE oi.product_id = 1
             AND o.created_at >= NOW() - INTERVAL 3 DAY

5. Database (MySQL)

   쿼리 실행 및 결과 반환:
   - Product 1: 150개 주문
   - Product 2: 120개 주문
   - Product 3: 180개 주문
   - ...

6. Response (서버 → 클라이언트)
   {
     "popularProducts": [
       {
         "productId": 3,
         "productName": "슬리퍼",
         "orderCount3Days": 180
       },
       {
         "productId": 1,
         "productName": "티셔츠",
         "orderCount3Days": 150
       },
       ...
     ]
   }
```

### 4.3 동시성 제어 흐름 (예: 선착순 쿠폰 발급)

```
요청 1 (Thread 1)          요청 2 (Thread 2)
   │                           │
   ├─ CouponService            ├─ CouponService
   │  issueCoupon(coupon_id)    │  issueCoupon(coupon_id)
   │                           │
   ├─ Repository 조회          ├─ Repository 조회
   │  findByIdForUpdate()       │  findByIdForUpdate()
   │  ↓                         │  ↓
   ├─ MySQL Lock 획득          ├─ 대기 (Lock 해제 대기)
   │  (SELECT ... FOR UPDATE)   │
   │  remaining_qty = 10        │
   │                           │
   ├─ 감소 처리                 │
   │  remaining_qty = 9         │
   │                           │
   ├─ 저장                      │
   │  UPDATE                    │
   │                           │
   ├─ Lock 해제                 │
   │  COMMIT                    │
   │                           ├─ Lock 획득
   │                           │ remaining_qty = 9
   │                           │
   │                           ├─ 감소 처리
   │                           │  remaining_qty = 8
   │                           │
   │                           ├─ 저장 및 Lock 해제
   │                           │  COMMIT

결과: remaining_qty = 8 (정확한 감소, 중복 발급 없음)
```

---

## 5. 엔티티-Repository-테이블 매핑 예시

### 5.1 User 엔티티 → 테이블 매핑

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "balance", nullable = false)
    private Long balance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

매핑 결과:
```
Java                  SQL
─────────────────     ─────────────────────
User 엔티티        → users 테이블
userId             → user_id (PK)
username           → username
balance            → balance
createdAt          → created_at
```

Repository 인터페이스:
```java
public interface UserRepository {
    Optional<User> findById(Long userId);      // SELECT * FROM users WHERE user_id = ?
    boolean existsById(Long userId);           // SELECT EXISTS(SELECT 1 FROM users WHERE user_id = ?)
    void save(User user);                      // INSERT ... or UPDATE ...
}
```

### 5.2 Order & OrderItem 관계 매핑

```java
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "user_id")
    private Long userId;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    private List<OrderItem> orderItems;
}

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "product_id")
    private Long productId;
}
```

매핑 관계:
```
orders 테이블           order_items 테이블
─────────────────       ─────────────────────
order_id (PK) ────┐    order_item_id (PK)
user_id           │    order_id (FK) ──────┘
order_status      │    product_id
created_at        └─── (1:다 관계)
```

---

## 6. 프로파일별 MySQL 설정

### 6.1 개발 환경 (application-dev.yml)

```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
  logging:
    level:
      com.hhplus.ecommerce: DEBUG
      org.hibernate.SQL: DEBUG
```

**특징**:
- SQL 쿼리 로깅 활성화 (개발 시 편의)
- Hibernate 쿼리 포맷팅 (가독성)
- DEBUG 로그 레벨

### 6.2 프로덕션 환경 (application-prod.yml)

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30   # 개발: 10 → 프로덕션: 30
  jpa:
    hibernate:
      ddl-auto: validate      # 개발: update → 프로덕션: validate
    show-sql: false           # 성능 최적화
  logging:
    level:
      root: WARN
```

**특징**:
- 연결 풀 증가 (30개 → 동시 요청 처리)
- DDL 검증만 (스키마 자동 변경 금지)
- SQL 로깅 비활성화 (성능)
- WARN 로그만 표출 (디버그 정보 제거)

---

## 7. 트랜잭션 관리

### 7.1 @Transactional 어노테이션

```java
@Repository
@Primary
@Transactional  // ← 모든 메서드에 적용
public class MySQLOrderRepository implements OrderRepository {

    @Override
    public Order save(Order order) {
        // 자동으로 트랜잭션 시작
        Order saved = orderJpaRepository.save(order);
        // 메서드 종료 시 자동으로 커밋
        return saved;
    }
}
```

**트랜잭션 생명주기**:
```
메서드 호출
    ↓
트랜잭션 시작 (BEGIN TRANSACTION)
    ↓
DB 작업 실행
    ↓
예외 발생?
├─ YES → 롤백 (ROLLBACK) → 변경사항 취소
└─ NO  → 커밋 (COMMIT)   → 변경사항 확정
    ↓
메서드 반환
```

### 7.2 복잡한 트랜잭션 예시

```java
@Service
public class OrderTransactionService {

    @Transactional
    public Order executeOrder(CreateOrderCommand cmd) {
        // 1. Order 저장
        Order order = new Order(...);
        Order savedOrder = orderRepository.save(order);

        // 2. OrderItem 저장 (Cascade로 자동 처리)
        // orderItems는 Order에 포함되어 있으므로 자동 저장

        // 3. 재고 감소
        for (OrderItemCommand item : cmd.getItems()) {
            ProductOption option = productRepository.findOptionById(item.getOptionId()).orElseThrow();
            option.decreaseStock(item.getQuantity());
            productRepository.saveOption(option);
        }

        // 4. 잔액 감소
        User user = userRepository.findById(cmd.getUserId()).orElseThrow();
        user.decreaseBalance(savedOrder.getFinalAmount());
        userRepository.save(user);

        // 모든 작업 성공 시 커밋
        // 중간에 예외 발생 시 롤백 (모든 변경사항 취소)
        return savedOrder;
    }
}
```

**특징**:
- 4개의 DB 작업이 하나의 트랜잭션으로 처리
- 모든 작업이 성공하거나 모두 실패
- ACID 보장 (Atomicity, Consistency, Isolation, Durability)

---

## 8. 문제 해결 가이드

### 8.1 NoUniqueBeanDefinitionException

**증상**: 애플리케이션 시작 실패
```
No qualifying bean of type 'UserRepository' available
```

**원인**: MySQL과 InMemory Repository 모두 `@Repository`인데 `@Primary` 없음

**해결**:
```java
@Repository
@Primary  // ← 추가
public class MySQLUserRepository implements UserRepository {
    // ...
}
```

### 8.2 데이터 동시성 문제 (Race Condition)

**증상**: 쿠폰 10개 한정인데 15개가 발급됨

**원인**: 동시 요청 시 각각 remaining_qty=10을 읽고 9로 감소하는 문제

**해결**: 비관적 락 사용
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM Coupon c WHERE c.couponId = :couponId")
Optional<Coupon> findByIdForUpdate(@Param("couponId") Long couponId);
```

### 8.3 N+1 쿼리 문제

**증상**: 상품 100개 조회 시 101개의 쿼리 실행 (1 + 100)

**원인**:
```java
List<Product> products = productRepository.findAll();  // Query 1
for (Product p : products) {
    List<ProductOption> options = productRepository.findOptionsByProductId(p.getProductId());  // Queries 2-101
}
```

**해결**: Eager Loading 또는 배치 쿼리
```java
@Entity
public class Product {
    @OneToMany(fetch = FetchType.EAGER)  // 즉시 로드
    private List<ProductOption> options;
}
```

### 8.4 느린 쿼리 디버깅

**1단계**: 로그에서 SQL 확인 (application-dev.yml)
```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

**2단계**: MySQL Workbench에서 실행 계획 확인
```sql
EXPLAIN SELECT COUNT(DISTINCT o.order_id)
FROM orders o
INNER JOIN order_items oi ON o.order_id = oi.order_id
WHERE oi.product_id = 1
AND o.created_at >= NOW() - INTERVAL 3 DAY;
```

**3단계**: 인덱스 추가 (필요시)
```sql
CREATE INDEX idx_order_items_product_id ON order_items(product_id);
CREATE INDEX idx_orders_created_at ON orders(created_at);
```

---

## 9. 요약

### 9.1 핵심 개념

| 개념 | 설명 | 예시 |
|------|------|------|
| **Port** | Domain 계층의 인터페이스 | `UserRepository` (interface) |
| **Adapter** | Infrastructure 계층의 구현체 | `MySQLUserRepository` (@Primary) |
| **@Primary** | Spring 빈 주입 우선순위 | MySQL이 자동으로 주입됨 |
| **JPA Repository** | Spring Data JPA 인터페이스 | `UserJpaRepository extends JpaRepository<User, Long>` |
| **@Query** | 커스텀 JPQL 쿼리 | `@Query("SELECT u FROM User u WHERE u.username = :username")` |
| **@Transactional** | 트랜잭션 경계 | 예외 시 자동 롤백 |
| **HikariCP** | 커넥션 풀 | 효율적인 DB 연결 관리 |
| **@Lock** | 동시성 제어 | `PESSIMISTIC_WRITE` = `SELECT ... FOR UPDATE` |

### 9.2 개발자 체크리스트

```
□ Repository 추가 시 InMemory + MySQL 쌍으로 구현
□ MySQL Repository에 @Repository, @Primary, @Transactional 어노테이션 추가
□ 도메인 계층에 Port 인터페이스 정의
□ JPA Repository에 필요한 @Query 메서드 정의
□ 복잡한 비즈니스 로직은 Service에 @Transactional 추가
□ 동시성 필요 시 @Lock(PESSIMISTIC_WRITE) 사용
□ 개발 중에는 application-dev.yml 사용 (show-sql: true)
□ 프로덕션은 application-prod.yml 사용 (validate, 대규모 풀)
□ N+1 쿼리 문제 확인 (Eager/Lazy Loading)
□ 느린 쿼리는 EXPLAIN으로 분석 후 인덱스 추가
```

### 9.3 참고 리소스

- **Spring Data JPA 공식 문서**: https://spring.io/projects/spring-data-jpa
- **Hibernate 공식 문서**: https://hibernate.org/orm/
- **MySQL 커넥터/J**: https://dev.mysql.com/doc/connector-j/
- **HikariCP 문서**: https://github.com/brettwooldridge/HikariCP

---

**문서 작성일**: 2025-11-12
**대상**: 새로 합류한 개발자
**목표**: MySQL 연동 구조와 Repository 동작의 완벽한 이해
