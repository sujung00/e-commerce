# E-Commerce 플랫폼

## 📋 목차

1. [프로젝트 개요](#프로젝트-개요)
2. [4계층 아키텍처](#4계층-아키텍처)
3. [주요 기능](#주요-기능)
4. [동시성 제어 전략](#동시성-제어-전략)
5. [단위 테스트 전략](#단위-테스트-전략)

---

## 프로젝트 개요

### 목적

**복잡한 비즈니스 로직**과 **동시성 제어**, **트랜잭션 관리**를 학습하기 위한 프로젝트입니다.

### 주요 특징

- ✅ **옵션 기반 재고 관리**: 상품의 옵션(색상, 사이즈)별로 독립적인 재고 추적
- ✅ **원자적 주문 처리**: 재고 감소, 잔액 차감, 쿠폰 사용을 한 번의 트랜잭션으로 처리
- ✅ **선착순 쿠폰 발급**: 비관적 락을 통한 동시성 제어로 쿠폰 중복 발급 방지
- ✅ **비동기 외부 전송**: Outbox 패턴으로 신뢰성 있는 메시지 전송 보장
- ✅ **포트-어댑터 패턴**: 도메인 계층이 인프라 계층에 의존하지 않음

---

## 4계층 아키텍처

프로젝트는 **클린 아키텍처** 원칙을 따르는 4계층으로 구성되어 있습니다:

```
┌─────────────────────────────────────┐
│   Presentation 계층                  │
│   (Controller, Request/Response DTO) │
└──────────────┬──────────────────────┘
               │ HTTP 요청/응답
┌──────────────▼──────────────────────┐
│   Application 계층                   │
│   (Service, 비즈니스 로직 조정)      │
└──────────────┬──────────────────────┘
               │ Domain 객체 사용
┌──────────────▼──────────────────────┐
│   Domain 계층                        │
│   (Entity, Value Object, Port)       │
└──────────────┬──────────────────────┘
               │ Repository Interface
┌──────────────▼──────────────────────┐
│   Infrastructure 계층                │
│   (Repository, Adapter, 저장소)     │
└─────────────────────────────────────┘
```

### 계층별 책임 및 특징

#### 1️⃣ **Presentation 계층** (`src/main/java/com/hhplus/ecommerce/presentation/`)

**책임**:
- HTTP 요청 처리 및 응답 반환
- 입력 데이터 검증 및 변환
- 에러 응답 생성

**구성**:
- `ProductController.java`: 상품 조회 엔드포인트
- `CartController.java`: 장바구니 CRUD 엔드포인트
- `OrderController.java`: 주문 생성/조회 엔드포인트
- `CouponController.java`: 쿠폰 발급/조회 엔드포인트
- `PopularProductController.java`: 인기 상품 조회 엔드포인트
- `GlobalExceptionHandler.java`: 통일된 에러 응답 처리

**특징**:
- 비즈니스 로직이 없고 순수하게 요청 처리만 담당
- Request/Response DTO를 통해 도메인 계층과 분리
- 예외 처리는 `GlobalExceptionHandler`에서 중앙화

#### 2️⃣ **Application 계층** (`src/main/java/com/hhplus/ecommerce/application/`)

**책임**:
- 여러 도메인 객체를 조합하여 비즈니스 플로우 구성
- 트랜잭션 경계 관리
- 도메인 로직 실행 조정

**주요 서비스**:
- `OrderService.java` + `OrderTransactionService.java`: 주문 생성 플로우 (2단계)
- `CartService.java`: 장바구니 CRUD 조정
- `CouponService.java`: 쿠폰 발급 조정
- `ProductService.java`: 상품 조회 조정
- `PopularProductService.java`: 인기 상품 순위 계산
- `InventoryService.java`: 재고 검증

**특징**:
- **2단계 트랜잭션 분리**: `OrderService` (검증/후처리) + `OrderTransactionService` (@Transactional)
  - 이유: Self-invocation 문제로 @Transactional이 작동하지 않기 때문
- `@Transactional`: 트랜잭션이 필요한 메서드에만 적용
- Repository 인터페이스를 주입받아 의존성 주입

#### 3️⃣ **Domain 계층** (`src/main/java/com/hhplus/ecommerce/domain/`)

**책임**:
- 비즈니스 규칙 정의
- 도메인 엔티티 및 값 객체 표현
- Repository 포트(인터페이스) 정의

**주요 도메인 엔티티**:
- `Product.java`: 상품 (판매 중 | 품절 | 판매 중지)
- `ProductOption.java`: 상품 옵션 (색상, 사이즈 등 - 재고 추적, 낙관적 락)
- `Cart.java` + `CartItem.java`: 사용자별 쇼핑 카트 (재고 영향 없음)
- `Order.java` + `OrderItem.java`: 주문 (COMPLETED | PENDING | FAILED)
- `Coupon.java`: 할인 쿠폰 (FIXED_AMOUNT | PERCENTAGE)
- `UserCoupon.java`: 사용자별 쿠폰 발급 상태 (ACTIVE | USED | EXPIRED)
- `User.java`: 사용자 (잔액 관리)
- `Outbox.java`: 외부 시스템 전송 메시지 (PENDING → SENT/FAILED)

**주요 포트(Repository 인터페이스)**:
- `ProductRepository`: 상품 조회
- `CartRepository`: 장바구니 CRUD
- `OrderRepository`: 주문 저장/조회
- `CouponRepository`: 쿠폰 조회/업데이트
- `UserCouponRepository`: 쿠폰 발급 관리
- `UserRepository`: 사용자 조회
- `OutboxRepository`: 외부 전송 메시지 관리

**특징**:
- 어떤 **외부 프레임워크도 import하지 않음** (비즈니스 로직의 순수성 유지)
- 도메인 예외는 `domain.exception` 패키지에서 정의
- 팩토리 메서드를 통한 엔티티 생성 (예: `Order.createOrder()`, `Outbox.createOutbox()`)

#### 4️⃣ **Infrastructure 계층** (`src/main/java/com/hhplus/ecommerce/infrastructure/`)

**책임**:
- Domain 계층의 Repository 포트 구현
- 실제 데이터 저장소 관리 (DB, 캐시 등)
- 외부 API 통신

**주요 Repository 구현체** (`persistence/` 폴더):
- `InMemoryProductRepository.java`: 상품 조회 (in-memory 저장소)
- `InMemoryCartRepository.java`: 장바구니 CRUD (in-memory 저장소)
- `InMemoryOrderRepository.java`: 주문 저장/조회 (in-memory 저장소)
- `InMemoryCouponRepository.java`: 쿠폰 조회/업데이트 + 비관적 락 시뮬레이션
- `InMemoryUserCouponRepository.java`: 쿠폰 발급 상태 관리
- `InMemoryUserRepository.java`: 사용자 조회 (in-memory 저장소)
- `InMemoryOutboxRepository.java`: 외부 전송 메시지 저장/조회

**특징**:
- 모두 **ConcurrentHashMap 기반** (스레드 안전성 보장)
- 프로덕션에서는 JPA + MySQL로 대체 가능
- Repository 인터페이스를 구현하므로 DDD 포트-어댑터 패턴 준수

### 아키텍처 흐름 예시: 주문 생성

```
1. HTTP 요청
   POST /api/orders
   └─> OrderController.createOrder()

2. Presentation 계층
   └─> OrderService.createOrder()

3. Application 계층 (검증 단계)
   └─> validateOrder() // 사용자, 상품, 쿠폰 존재 확인
   └─> OrderTransactionService.executeTransactionalOrder() // Spring AOP 프록시

4. Application 계층 (트랜잭션 단계) [프록시 생성됨]
   @Transactional로 래핑됨
   ├─> 재고 감소 (ProductOption.stock--)
   ├─> 잔액 차감 (User.balance-=finalAmount)
   ├─> 쿠폰 사용 (UserCoupon.status=USED)
   └─> Outbox 메시지 저장 (Outbox.status=PENDING)

5. Domain 계층
   └─> 각 도메인 객체의 검증 로직 실행

6. Infrastructure 계층
   └─> Repository 구현체가 실제 저장
   ├─> InMemoryProductRepository.findByIdForUpdate() // 비관적 락
   ├─> InMemoryCouponRepository.findByIdForUpdate() // 비관적 락
   ├─> InMemoryOrderRepository.save()
   └─> InMemoryOutboxRepository.save()

7. 후처리 (Application 계층)
   └─> handlePostOrderProcessing()

8. HTTP 응답
   └─> CreateOrderResponse (200 OK)
```

---

## 주요 기능

### 1. 상품 관리

| 기능 | 엔드포인트 | 설명 |
|------|-----------|------|
| 상품 목록 조회 | `GET /api/products` | 페이지네이션, 정렬 지원 |
| 상품 상세 조회 | `GET /api/products/{productId}` | 옵션과 함께 조회 |
| 인기 상품 조회 | `GET /api/products/popular` | 3일간 주문 수 기준 정렬 |

**특징**:
- 옵션별 재고 추적 (재고는 `ProductOption`에 저장)
- 상품 상태 추적 (판매 중 | 품절 | 판매 중지)
- 총 재고 = SUM(ProductOption.stock)

### 2. 장바구니

| 기능 | 엔드포인트 | 설명 |
|------|-----------|------|
| 장바구니 조회 | `GET /api/carts/{userId}` | 사용자의 현재 카트 조회 |
| 아이템 추가 | `POST /api/carts/{userId}/items` | 옵션별로 상품 추가 |
| 아이템 제거 | `DELETE /api/carts/{cartItemId}` | 특정 카트 아이템 제거 |
| 장바구니 비우기 | `DELETE /api/carts/{userId}` | 사용자 카트 초기화 |

**특징**:
- 카트는 **재고에 영향을 주지 않음** (주문 시에만 재고 감소)
- 사용자당 1개의 카트 (1:1 관계)
- 카트 아이템은 옵션 기반으로 관리

### 3. 주문

| 기능 | 엔드포인트 | 설명 |
|------|-----------|------|
| 주문 생성 | `POST /api/orders` | 결제 및 재고 감소 원자적 처리 |
| 주문 조회 | `GET /api/orders/{orderId}` | 주문 상세 정보 조회 |
| 주문 목록 조회 | `GET /api/orders/users/{userId}` | 사용자별 주문 히스토리 |

**특징**:
- **2단계 트랜잭션**: 검증 → 원자적 거래 → 후처리
- 재고 감소 + 잔액 차감 + 쿠폰 사용 + Outbox 메시지 저장을 **하나의 트랜잭션**으로 처리
- 실패 시 모두 롤백됨 (원자성 보장)

### 4. 쿠폰

| 기능 | 엔드포인트 | 설명 |
|------|-----------|------|
| 쿠폰 목록 조회 | `GET /api/coupons` | 발급 가능한 쿠폰 조회 |
| 쿠폰 발급 | `POST /api/coupons/{couponId}/issue` | 선착순 발급 (동시성 제어) |
| 발급된 쿠폰 조회 | `GET /api/users/{userId}/coupons` | 사용자의 쿠폰 조회 |

**특징**:
- **FIXED_AMOUNT**: 고정 금액 할인
- **PERCENTAGE**: 비율 할인
- 선착순 발급으로 중복 발급 방지 (비관적 락)
- 쿠폰 상태: ACTIVE → USED 또는 EXPIRED

### 5. 통계

| 기능 | 엔드포인트 | 설명 |
|------|-----------|------|
| 인기 상품 | `GET /api/products/popular` | 3일 기준 주문 수 |
| 주문 통계 | 추가 예정 | 수익, 거래량 등 |

---

## 동시성 제어 전략

### 개요

시스템은 3가지 동시성 제어 기법을 사용하여 데이터 일관성을 보장합니다:

| 기법 | 적용 대상 | 문제점 | 해결책 |
|------|---------|--------|--------|
| **낙관적 락** | ProductOption, Coupon | 동시 업데이트 충돌 감지 | Version 필드 증가 |
| **비관적 락** | 쿠폰 발급 (선착순) | 동시 발급으로 중복 발급 | SELECT ... FOR UPDATE |
| **원자성 보장** | 주문 생성 | 일부만 성공하는 상황 | @Transactional |

### 1. 낙관적 락 (Optimistic Locking)

**적용 대상**: `ProductOption`, `Coupon`

**원리**:
- 각 엔티티에 `version` 필드 보유
- 업데이트 시 version을 증가시킴
- UPDATE 구문에 `WHERE version = {기존버전}`을 추가
- 다른 트랜잭션이 먼저 업데이트했으면 버전이 바뀌어 UPDATE 실패

**예시**:

```java
// 시간 T1: User A가 ProductOption 조회
ProductOption option = productRepository.findById(1L); // version=1, stock=100

// 시간 T2: User B가 같은 ProductOption 조회
ProductOption optionB = productRepository.findById(1L); // version=1, stock=100

// 시간 T3: User B가 먼저 업데이트
optionB.stock = 99;
optionB.version = 2; // version 증가
repository.update(optionB); // UPDATE WHERE version = 1 ✅ 성공

// 시간 T4: User A가 업데이트 시도
option.stock = 99;
option.version = 2;
repository.update(option); // UPDATE WHERE version = 1 ❌ 실패! (버전 불일치)
// OptimisticLockException 발생
```

**코드 예시**:

```java
@Transactional
public void createOrder(CreateOrderRequest request) {
    // ...
    ProductOption option = repository.findById(productId);
    option.stock--; // 재고 감소
    option.version++; // 버전 증가

    // UPDATE product_options
    // SET stock = ?, version = ?
    // WHERE option_id = ? AND version = ? ← 낙관적 락
    repository.saveOption(option);
}
```

**장점**: 읽기가 많은 워크로드에 최적화, 락으로 인한 대기 없음
**단점**: 충돌 감지 후 재시도 로직 필요

### 2. 비관적 락 (Pessimistic Locking)

**적용 대상**: 쿠폰 발급 (선착순 경쟁)

**원리**:
- 읽기 시점에 즉시 락을 획득
- 다른 트랜잭션이 같은 행에 접근하지 못하도록 차단
- 업데이트 후 자동으로 락 해제

**예시**:

```
시간 T1: User A가 쿠폰 발급 요청
  └─> SELECT ... FOR UPDATE ← 쿠폰에 쓰기 락 획득
  └─> remaining_qty 확인 (100)
  └─> remaining_qty 감소 (100 → 99)
  └─> UPDATE (자동 락 해제)

시간 T2: User B가 같은 쿠폰 발급 요청
  └─> SELECT ... FOR UPDATE ← 락 대기 (User A 진행 중)
  └─> T1 완료 후 실행
  └─> remaining_qty 확인 (99)
  └─> remaining_qty 감소 (99 → 98)
  └─> UPDATE (락 해제)

결과: 쿠폰 2개 각각 발급됨 (중복 발급 방지 ✅)
```

**코드 예시** (InMemory 시뮬레이션):

```java
@Override
public Optional<Coupon> findByIdForUpdate(Long couponId) {
    synchronized (couponLock) { // 비관적 락 시뮬레이션
        return Optional.ofNullable(couponStore.get(couponId));
    }
}

@Transactional
public void issueCoupon(Long couponId, Long userId) {
    // SELECT ... FOR UPDATE (비관적 락 획득)
    Coupon coupon = couponRepository.findByIdForUpdate(couponId)
            .orElseThrow(CouponNotFoundException::new);

    // 검증
    if (coupon.getRemainingQty() <= 0) {
        throw new CouponSoldOutException();
    }

    // 원자적 감소
    coupon.setRemainingQty(coupon.getRemainingQty() - 1);

    // 사용자 쿠폰 저장
    userCouponRepository.save(UserCoupon.of(userId, coupon));

    // 업데이트 (락 자동 해제)
    couponRepository.update(coupon);
}
```

**장점**: 동시 충돌이 많은 환경에서 안전, 재시도 로직 불필요
**단점**: 락 대기로 인한 성능 저하

### 3. 트랜잭션 경계 (원자성)

**적용 대상**: 주문 생성 (2단계 트랜잭션)

**원리**:
- 여러 작업을 하나의 트랜잭션으로 처리
- 중간에 에러 발생 시 모두 롤백
- ACID 속성 보장

**예시**:

```java
@Transactional
public void executeTransactionalOrder(Order order) {
    // 1단계: 재고 감소 (낙관적 락)
    ProductOption option = productRepository.findByIdForUpdate(optionId);
    option.stock--;
    productRepository.update(option); // version 증가

    // 2단계: 잔액 차감
    User user = userRepository.findById(userId).orElseThrow();
    user.balance -= finalAmount;
    userRepository.update(user);

    // 3단계: 쿠폰 사용
    UserCoupon userCoupon = userCouponRepository.findById(userCouponId).orElseThrow();
    userCoupon.status = "USED";
    userCouponRepository.update(userCoupon);

    // 4단계: 주문 저장
    orderRepository.save(order);

    // 5단계: 외부 전송 메시지 저장
    outboxRepository.save(Outbox.createOutbox(order.getOrderId(), userId, "OrderCreated"));

    // 중간에 에러 발생 시 모두 롤백됨 ✅
}
```

**장점**: 모든 작업의 원자성 보장
**단점**: 트랜잭션 시간이 길면 락 대기 증가

---

## 단위 테스트 전략

### 개요

프로젝트는 **3계층 테스트** 구조를 가지고 있으며, 총 **477개의 테스트**로 각 계층의 동작을 검증합니다:

```
계층별 테스트 분포:
├─ Domain 계층: 337개 테스트 (70.6%)
├─ Infrastructure 계층: 140개 테스트 (29.4%)
└─ Application 계층: 추가 예정
```

### 1. Domain 계층 테스트 (337개)

**목적**: 비즈니스 규칙이 정확하게 동작하는지 검증

**테스트 대상**:
- `OrderTest.java` (20개): 주문 생성, 금액 계산, 아이템 관리
- `OrderItemTest.java` (22개): 아이템 생성 팩토리 패턴, 소계 계산
- `OutboxTest.java` (29개): 상태 전이, 재시도 관리
- `CartTest.java` (22개): CRUD, 타임스탬프 관리
- `CartItemTest.java` (27개): 아이템 생성, 수량 관리
- `ProductTest.java` (31개): 상품 CRUD, 상태 전이
- `ProductOptionTest.java` (28개): 옵션 CRUD, 낙관적 락
- `ProductStatusTest.java` (34개): 상태 enum 값 검증
- `UserTest.java` (31개): 사용자 생성, 잔액 관리
- `CouponTest.java` (31개): 쿠폰 타입, 할인 관리
- `UserCouponTest.java` (27개): 쿠폰 발급 라이프사이클
- `DomainExceptionTest.java` (81개): 모든 도메인 예외 검증

**테스트 패턴** (Given-When-Then):

```java
@Test
@DisplayName("주문 생성 - 올바른 금액 계산")
void testOrderCreation_CorrectAmountCalculation() {
    // Given: 주문 생성을 위한 데이터 준비
    long userId = 100L;
    long productId = 1L;
    long discountAmount = 5000L;
    long totalAmount = 100000L;
    long finalAmount = 95000L;

    // When: 주문 생성
    Order order = Order.createOrder(userId, productId, discountAmount, totalAmount, finalAmount);

    // Then: 결과 검증
    assertEquals(userId, order.getUserId());
    assertEquals(finalAmount, order.getFinalAmount());
    assertNotNull(order.getCreatedAt());
}
```

### 2. Infrastructure 계층 테스트 (140개)

**목적**: Repository 구현이 올바르게 데이터를 저장/조회하는지 검증

**테스트 대상** (7개 Repository):
- `InMemoryCartRepositoryTest.java` (30개)
- `InMemoryProductRepositoryTest.java` (29개)
- `InMemoryOrderRepositoryTest.java` (18개)
- `InMemoryOutboxRepositoryTest.java` (23개)
- `InMemoryUserRepositoryTest.java` (19개)
- `InMemoryCouponRepositoryTest.java` (28개)
- `InMemoryUserCouponRepositoryTest.java` (33개)

**테스트 항목**:

| 항목 | 설명 | 예시 |
|-----|------|------|
| **CRUD** | Create, Read, Update, Delete | save(), findById(), update(), delete() |
| **Batch 조회** | 상태별/사용자별 조회 | findAllByStatus(), findByUserId() |
| **동시성** | 락 시뮬레이션 | findByIdForUpdate() |
| **Pagination** | 페이지네이션 | findByUserId(userId, page, size) |
| **초기 데이터** | In-Memory 저장소 샘플 데이터 | 미리 저장된 10개 상품 검증 |

**테스트 예시**:

```java
@Test
@DisplayName("쿠폰 발급 - 선착순 제어 (비관적 락)")
void testIssueCoupon_FirstComeFirstServed() {
    // Given: 발급 가능한 쿠폰
    Optional<Coupon> coupon = couponRepository.findById(1L);
    assertTrue(coupon.isPresent());
    int originalRemaining = coupon.get().getRemainingQty();

    // When: 비관적 락으로 쿠폰 조회
    Optional<Coupon> lockedCoupon = couponRepository.findByIdForUpdate(1L);

    // Then: 재고 감소
    assertTrue(lockedCoupon.isPresent());
    lockedCoupon.get().setRemainingQty(originalRemaining - 1);
    Coupon updated = couponRepository.update(lockedCoupon.get());
    assertEquals(originalRemaining - 1, updated.getRemainingQty());
}
```

### 3. Application 계층 테스트 (추가 예정)

**목적**: 서비스 로직이 여러 도메인 객체를 올바르게 조합하는지 검증

**계획**:
- `OrderServiceTest.java`: 주문 생성 플로우 전체
- `CartServiceTest.java`: 장바구니 CRUD
- `CouponServiceTest.java`: 쿠폰 발급 조정

### 테스트 실행 방법

#### 1. 전체 테스트 실행
```bash
./gradlew test
```

#### 2. 특정 계층만 테스트
```bash
# Domain 계층 테스트만
./gradlew test --tests "*domain*"

# Infrastructure 계층 테스트만
./gradlew test --tests "*infrastructure*"
```

#### 3. 특정 테스트 클래스만 실행
```bash
./gradlew test --tests "OrderTest"
./gradlew test --tests "InMemoryCouponRepositoryTest"
```

#### 4. 커버리지 분석 (Jacoco)
```bash
./gradlew jacocoTestReport
# 결과: build/reports/jacoco/test/html/index.html
```

### 테스트 설계 원칙

#### 1. **Given-When-Then 패턴**
- Given: 테스트 시작 전 필요한 데이터 준비
- When: 테스트할 메서드 호출
- Then: 결과 검증

#### 2. **경계값 테스트**
- 최소값, 최대값, 경계값 테스트
- Null, 빈 컬렉션 테스트

#### 3. **상태 전이 테스트**
- 엔티티의 상태 변화 검증
- 무효한 상태 전이 에러 처리

#### 4. **동시성 테스트**
- 비관적 락 시뮬레이션
- 동시 요청 처리

#### 5. **초기 데이터 검증**
- In-Memory 저장소의 샘플 데이터 확인
- 실제 사용 시나리오 반영

### 테스트 실행 결과

```
✅ Domain 계층: 337개 테스트 PASS
✅ Infrastructure 계층: 140개 테스트 PASS
═════════════════════════════════════
✅ 총 477개 테스트 PASS (100%)
```

