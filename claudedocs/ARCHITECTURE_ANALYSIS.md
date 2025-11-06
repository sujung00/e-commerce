# E-Commerce 프로젝트 아키텍처 분석 보고서

**작성일**: 2025-11-06
**프로젝트**: HH+ e-commerce
**Java 버전**: 17
**Spring Boot 버전**: 3.5.7

---

## 📋 목차

1. [현재 패키지 구조](#1-현재-패키지-구조)
2. [계층별 주요 클래스와 역할](#2-계층별-주요-클래스와-역할)
3. [계층 간 의존성 흐름](#3-계층-간-의존성-흐름)
4. [인메모리 DB 구현 분석](#4-인메모리-db-구현-분석)
5. [DTO, Repository, Service, Controller 관계](#5-dto-repository-service-controller-관계)
6. [아키텍처 평가](#6-아키텍처-평가)
7. [주요 발견사항 및 개선사항](#7-주요-발견사항-및-개선사항)

---

## 1. 현재 패키지 구조

### 1.1 전체 디렉토리 맵

```
com.hhplus.ecommerce/
│
├── ECommerceApplication.java (엔트리 포인트)
│
├── presentation/ (프레젠테이션 계층 - 34.1%)
│   ├── ProductController.java ⚠️ (루트에 위치)
│   ├── PopularProductController.java ⚠️ (루트에 위치)
│   ├── controller/
│   │   └── CartController.java ✅
│   ├── dto/
│   │   ├── request/
│   │   │   ├── AddCartItemRequest.java
│   │   │   └── UpdateQuantityRequest.java
│   │   └── response/
│   │       ├── CartItemResponse.java
│   │       ├── CartResponseDto.java
│   │       ├── ProductListResponse.java
│   │       ├── ProductDetailResponse.java
│   │       ├── ProductResponse.java
│   │       ├── ProductOptionResponse.java
│   │       ├── PopularProductListResponse.java
│   │       ├── PopularProductView.java
│   │       └── ErrorResponse.java
│
├── application/ (애플리케이션 계층 - 9.8%)
│   ├── PopularProductService.java (인터페이스, 루트) ⚠️
│   ├── PopularProductServiceImpl.java (구현, 루트) ⚠️
│   ├── ProductService.java (구현, 루트) ⚠️
│   └── service/
│       └── CartService.java ✅
│
├── domain/ (도메인 계층 - 39.0%) ★ 프로젝트 핵심
│   ├── Product.java (엔티티)
│   ├── ProductOption.java (엔티티)
│   ├── ProductStatus.java (Enum)
│   ├── Cart.java (엔티티)
│   ├── CartItem.java (엔티티)
│   ├── User.java (엔티티)
│   ├── Order.java (엔티티)
│   ├── OrderItem.java (엔티티)
│   ├── Coupon.java (엔티티)
│   ├── UserCoupon.java (엔티티)
│   ├── Outbox.java (이벤트 아우트박스)
│   ├── repository/ (포트/인터페이스)
│   │   ├── ProductRepositoryPort.java
│   │   ├── CartRepositoryPort.java
│   │   └── UserRepositoryPort.java
│   └── exception/ (도메인 예외)
│       ├── CartItemNotFoundException.java
│       ├── InvalidQuantityException.java
│       └── UserNotFoundException.java
│
├── infrastructure/ (인프라 계층 - 7.3%)
│   └── persistence/
│       ├── InMemoryProductRepository.java (포트 구현)
│       ├── InMemoryCartRepository.java (포트 구현)
│       └── InMemoryUserRepository.java (포트 구현)
│
├── common/ (공통 계층 - 4.9%)
│   └── exception/
│       ├── GlobalExceptionHandler.java (@RestControllerAdvice)
│       └── ProductNotFoundException.java
│
└── config/ (설정 계층 - 2.4%)
    └── WebConfig.java (@Configuration)
```

### 1.2 패키지 구조 평가

| 항목 | 현황 | 평가 |
|------|------|------|
| **Presentation 위치** | ProductController, PopularProductController가 루트에 위치 | ⚠️ 일관성 부족 |
| **Application 위치** | ProductService, PopularProductService가 루트에 위치 | ⚠️ 일관성 부족 |
| **Domain 위치** | 모든 엔티티와 포트가 `domain/` 하위에 정리됨 | ✅ 우수 |
| **Infrastructure 위치** | 모든 구현체가 `infrastructure/persistence/` 하위에 정리됨 | ✅ 우수 |
| **DTO 위치** | 모든 DTO가 `presentation/dto/` 하위에 정리됨 | ✅ 우수 |
| **Exception 위치** | 도메인/공통 예외가 분리되어 있음 | ✅ 우수 |

---

## 2. 계층별 주요 클래스와 역할

### 2.1 Domain Layer (도메인 계층) - 프로젝트의 중심 (39.0%)

#### 📌 엔티티 (Entity)

**Product 엔티티**
```java
// 위치: domain/Product.java
- productId: Long (PK)
- productName: String
- description: String
- price: Long
- totalStock: Integer (계산 필드: product_options의 재고 합계)
- status: String (ProductStatus Enum 값: 판매중, 품절, 판매중지)
- createdAt, updatedAt: LocalDateTime
```
**역할**: 상품의 기본 정보와 재고 상태를 관리합니다.

**ProductOption 엔티티**
```java
// 위치: domain/ProductOption.java
- optionId: Long (PK)
- productId: Long (FK)
- name: String (예: "블랙/M", "청색/32")
- stock: Integer
- version: Long (낙관적 잠금용)
- createdAt, updatedAt: LocalDateTime
```
**역할**: 상품의 옵션(색상, 사이즈 등)별 재고를 관리합니다.

**Cart 엔티티**
```java
// 위치: domain/Cart.java
- cartId: Long (PK)
- userId: Long (FK, 1:1 관계)
- totalItems: Integer (계산 필드: cart_items 개수)
- totalPrice: Long (계산 필드: cart_items 소계 합)
- createdAt, updatedAt: LocalDateTime
```
**역할**: 사용자별 장바구니 정보를 관리합니다.

**CartItem 엔티티**
```java
// 위치: domain/CartItem.java
- cartItemId: Long (PK)
- cartId: Long (FK)
- productId: Long (FK)
- optionId: Long (FK)
- quantity: Integer
- unitPrice: Long
- subtotal: Long (quantity × unitPrice)
- createdAt, updatedAt: LocalDateTime
```
**역할**: 장바구니에 담긴 개별 상품 정보를 관리합니다.

**기타 엔티티** (정의됨, 미구현)
- `User.java`: 사용자 정보
- `Order.java`: 주문 정보
- `OrderItem.java`: 주문 아이템
- `Coupon.java`: 쿠폰 정보
- `UserCoupon.java`: 사용자-쿠폰 매핑
- `Outbox.java`: 이벤트 소싱용 아우트박스

#### 📌 포트 (Port) - 의존성 역전의 핵심

**ProductRepositoryPort**
```java
// 위치: domain/repository/ProductRepositoryPort.java
인터페이스 메서드:
- List<Product> findAll()
- Optional<Product> findById(Long)
- List<ProductOption> findOptionsByProductId(Long)
- Optional<ProductOption> findOptionById(Long)
- Long getOrderCount3Days(Long productId)  // 인기상품 계산용
- void save(Product)
- void saveOption(ProductOption)
```
**역할**: 상품 데이터 접근을 추상화합니다. (실제 구현은 Infrastructure)

**CartRepositoryPort**
```java
// 위치: domain/repository/CartRepositoryPort.java
인터페이스 메서드:
- Cart findOrCreateByUserId(Long)
- Optional<Cart> findByUserId(Long)
- Optional<CartItem> findCartItemById(Long)
- CartItem saveCartItem(CartItem)
- void deleteCartItem(Long)
- Cart saveCart(Cart)
```
**역할**: 장바구니 데이터 접근을 추상화합니다.

**UserRepositoryPort**
```java
// 위치: domain/repository/UserRepositoryPort.java
인터페이스 메서드:
- Optional<User> findById(Long)
- boolean existsById(Long)
- void save(User)
```
**역할**: 사용자 데이터 접근을 추상화합니다.

#### 📌 도메인 예외

- `CartItemNotFoundException`: 장바구니 아이템을 찾을 수 없을 때
- `InvalidQuantityException`: 수량이 유효하지 않을 때 (1~1000 범위)
- `UserNotFoundException`: 사용자를 찾을 수 없을 때

---

### 2.2 Application Layer (애플리케이션 계층) - 비즈니스 로직 (9.8%)

#### 📌 ProductService (⚠️ 루트 위치)

```java
// 위치: application/ProductService.java
클래스 구조: 구현체 (인터페이스 없음)

주요 메서드:
- ProductListResponse getProductList(page, size, sort)
  → 페이지네이션 + 정렬 지원
  → 정렬 필드: product_id, product_name, price, created_at
  → 정렬 방향: asc, desc

- ProductDetailResponse getProductDetail(productId)
  → 상품 상세정보 + 옵션 조회
  → 상품 없으면 ProductNotFoundException 발생

역할: 상품 조회 비즈니스 로직 처리
```

**특징**:
- Infrastructure(InMemoryProductRepository)에서 데이터 조회
- Presentation DTOs로 변환
- 파라미터 검증 포함 (page, size, sort)
- 클라이언트 요청 처리의 중간 계층

#### 📌 PopularProductService & PopularProductServiceImpl

```java
// 인터페이스: application/PopularProductService.java
// 구현체: application/PopularProductServiceImpl.java (⚠️ 루트 위치)

주요 메서드:
- PopularProductListResponse getPopularProducts()
  → 최근 3일 주문 수량 기준 상위 5개 상품
  → 1시간 TTL 캐싱 적용
  → Infrastructure(ProductRepository.getOrderCount3Days())에서 동적 계산

캐싱 구조:
- ConcurrentHashMap<String, CachedResponse>
- 캐시 키: "popular_products"
- 캐시 TTL: 3600초 (1시간)
- 내부 클래스: CachedResponse (응답 + 타임스탬프)

역할: 인기상품 조회 및 캐싱 처리
```

#### 📌 CartService (✅ service/ 패키지에 정리됨)

```java
// 위치: application/service/CartService.java

주요 메서드:
- CartResponseDto getCartByUserId(Long userId)
  → 사용자의 장바구니 조회

- CartItemResponse addItem(Long userId, AddCartItemRequest)
  → 장바구니에 상품 추가
  → 수량 검증 (1~1000)
  → 사용자 존재 확인

- CartItemResponse updateItemQuantity(Long userId, Long cartItemId, UpdateQuantityRequest)
  → 장바구니 아이템 수량 수정
  → 사용자 소유권 확인

- void removeItem(Long userId, Long cartItemId)
  → 장바구니에서 아이템 제거

내부 메서드:
- validateQuantity(Integer)
- updateCartTotals(Cart)
- getProductName(Long) // 하드코딩된 샘플 데이터
- getOptionName(Long)
- getProductPrice(Long)

역할: 장바구니 관리 비즈니스 로직
```

**⚠️ 주의사항**: 상품명, 옵션명, 가격을 하드코딩된 switch 문으로 조회 (나중에 Repository 의존성으로 변경 필요)

---

### 2.3 Presentation Layer (프레젠테이션 계층) - 클라이언트 인터페이스 (34.1%)

#### 📌 컨트롤러

**ProductController** (⚠️ 루트 위치)
```java
// 위치: presentation/ProductController.java
엔드포인트:
- GET /products
  → 쿼리파라미터: page (기본값: 0), size (기본값: 10, 범위: 1~100), sort (기본값: product_id,desc)
  → 응답: ProductListResponse

- GET /products/{product_id}
  → 경로파라미터: product_id
  → 응답: ProductDetailResponse

의존성: ProductService
```

**PopularProductController** (⚠️ 루트 위치)
```java
// 위치: presentation/PopularProductController.java
엔드포인트:
- GET /popular-products
  → 쿼리파라미터: 없음
  → 응답: PopularProductListResponse (상위 5개, rank 포함)

의존성: PopularProductService
```

**CartController** (✅ controller/ 패키지에 정리됨)
```java
// 위치: presentation/controller/CartController.java
엔드포인트:
- GET /carts
  → 헤더파라미터: X-USER-ID
  → 응답: CartResponseDto

- POST /carts/items
  → 헤더파라미터: X-USER-ID
  → 요청본문: AddCartItemRequest
  → 응답: CartItemResponse (상태: 201 Created)

- PUT /carts/items/{cart_item_id}
  → 헤더파라미터: X-USER-ID
  → 경로파라미터: cart_item_id
  → 요청본문: UpdateQuantityRequest
  → 응답: CartItemResponse

- DELETE /carts/items/{cart_item_id}
  → 헤더파라미터: X-USER-ID
  → 경로파라미터: cart_item_id
  → 응답: 204 No Content

의존성: CartService
```

#### 📌 DTO (Data Transfer Object)

**요청 DTO**
```java
// AddCartItemRequest
- productId: Long (필수)
- optionId: Long (필수)
- quantity: Integer (필수, 1~1000)

// UpdateQuantityRequest
- quantity: Integer (필수, 1~1000)
```

**응답 DTO**
```java
// ProductResponse
- productId, productName, description, price, totalStock, status, createdAt

// ProductDetailResponse
- productId, productName, description, price, totalStock, status
- options: List<ProductOptionResponse>
- createdAt

// ProductOptionResponse
- optionId, name, stock, version

// PopularProductView (인기상품 뷰)
- productId, productName, price, totalStock, status
- orderCount3Days: Long (최근 3일 주문 수)
- rank: Integer (1~5)
- createdAt

// PopularProductListResponse
- products: List<PopularProductView>

// CartResponseDto
- cartId, userId, totalItems, totalPrice
- items: List<CartItemResponse>
- updatedAt

// CartItemResponse
- cartItemId, cartId, productId, optionId
- productName, optionName
- quantity, unitPrice, subtotal
- createdAt, updatedAt

// ErrorResponse
- errorCode: String
- message: String
```

---

### 2.4 Infrastructure Layer (인프라 계층) - 데이터 접근 (7.3%)

#### 📌 Repository 구현체

**InMemoryProductRepository**
```java
// 위치: infrastructure/persistence/InMemoryProductRepository.java
// @Repository 어노테이션으로 등록됨

내부 저장소:
- products: HashMap<Long, Product> (10개 상품 초기화)
- productOptions: HashMap<Long, ProductOption> (7개 옵션 초기화)
- productToOptionsMap: HashMap<Long, List<Long>> (상품-옵션 매핑)
- orderCount3DaysMap: HashMap<Long, Long> (최근 3일 주문 수)

초기화 데이터:
상품 1~10: 티셔츠, 청바지, 슬리퍼, 후드, 치마, 운동화, 스카프(품절), 모자, 장갑(판매중지), 양말
상품 1, 2, 3에만 옵션 지정됨

포트 구현:
✅ findAll() - 모든 상품 반환
✅ findById(Long) - 특정 상품 반환
✅ findOptionsByProductId(Long) - 상품의 옵션들 반환
✅ findOptionById(Long) - 특정 옵션 반환
✅ getOrderCount3Days(Long) - 최근 3일 주문 수 반환 (샘플 데이터 기반)
✅ save(Product) - 상품 저장
✅ saveOption(ProductOption) - 옵션 저장
```

**InMemoryCartRepository**
```java
// 위치: infrastructure/persistence/InMemoryCartRepository.java
// @Repository 어노테이션으로 등록됨

내부 저장소:
- carts: ConcurrentHashMap<Long, Cart>
- cartItems: ConcurrentHashMap<Long, CartItem>
- userCartMap: ConcurrentHashMap<Long, Long> (userId -> cartId 매핑)
- cartIdGenerator: AtomicLong
- cartItemIdGenerator: AtomicLong

초기화 데이터:
- Cart 1: User 100 (1개 아이템, 총액 59,800원)
- Cart 2: User 101 (1개 아이템, 총액 19,900원)

포트 구현:
✅ findOrCreateByUserId(Long) - 사용자의 장바구니 조회 또는 생성
✅ findByUserId(Long) - 사용자의 장바구니 조회
✅ findCartItemById(Long) - 특정 장바구니 아이템 조회
✅ saveCartItem(CartItem) - 장바구니 아이템 저장
✅ deleteCartItem(Long) - 장바구니 아이템 삭제
✅ saveCart(Cart) - 장바구니 저장

추가 메서드:
- getCartItems(Long cartId) - 특정 장바구니의 모든 아이템 반환
```

**InMemoryUserRepository**
```java
// 위치: infrastructure/persistence/InMemoryUserRepository.java
// @Repository 어노테이션으로 등록됨

내부 저장소:
- users: HashMap<Long, User> (샘플 사용자)

초기화 데이터:
- User 100, 101

포트 구현:
✅ findById(Long) - 특정 사용자 조회
✅ existsById(Long) - 사용자 존재 여부 확인
✅ save(User) - 사용자 저장
```

---

### 2.5 Common & Config Layer

#### 📌 GlobalExceptionHandler

```java
// 위치: common/exception/GlobalExceptionHandler.java
// @RestControllerAdvice 어노테이션으로 전역 예외 처리

처리하는 예외:
- ProductNotFoundException → 404 Not Found
- UserNotFoundException → 404 Not Found
- CartItemNotFoundException → 404 Not Found
- InvalidQuantityException → 400 Bad Request
- IllegalArgumentException → 400 Bad Request
- Exception (기타 모든 예외) → 500 Internal Server Error

응답 형식: ErrorResponse (errorCode, message)
```

#### 📌 WebConfig

```java
// 위치: config/WebConfig.java
// @Configuration 어노테이션으로 스프링 빈 등록

현재 역할: (추가 설정 없음, 비어있음)
향후 필요한 설정:
- JPA 설정 (JpaConfig)
- 캐시 설정 (CacheConfig)
- 트랜잭션 설정 (TransactionConfig)
```

---

## 3. 계층 간 의존성 흐름

### 3.1 정상 의존성 흐름 (클린 아키텍처)

```
┌──────────────────────────────────────────┐
│       Presentation Layer (외부)          │
│   ProductController, CartController      │
│   PopularProductController               │
│         ↓ 의존 ↓                        │
├──────────────────────────────────────────┤
│     Application Layer (비즈니스 로직)     │
│   ProductService, CartService            │
│   PopularProductService(Impl)            │
│         ↓ 의존 ↓                        │
├──────────────────────────────────────────┤
│    Domain Layer (비즈니스 규칙)          │
│  Product, Cart, User, CartItem...        │
│  ProductRepositoryPort (인터페이스)      │
│  CartRepositoryPort (인터페이스)         │
│  UserRepositoryPort (인터페이스)         │
│         ↑ 의존 (역전) ↑                 │
├──────────────────────────────────────────┤
│   Infrastructure Layer (구현)            │
│  InMemoryProductRepository               │
│  InMemoryCartRepository                  │
│  InMemoryUserRepository                  │
└──────────────────────────────────────────┘

핵심: Infrastructure는 Domain의 Port를 구현하므로
      Presentation → Application → Domain ← Infrastructure
      (의존성이 한 방향으로 흐름 ✅ 클린 아키텍처 준수)
```

### 3.2 의존성 상세 맵

#### Presentation → Application
```
ProductController
  ├─ depends on → ProductService
  └─ uses → ProductListResponse, ProductDetailResponse

PopularProductController
  ├─ depends on → PopularProductService
  └─ uses → PopularProductListResponse

CartController
  ├─ depends on → CartService
  ├─ uses → CartResponseDto, CartItemResponse
  └─ consumes → AddCartItemRequest, UpdateQuantityRequest
```

#### Application → Domain
```
ProductService
  ├─ depends on → ProductRepositoryPort (interface)
  ├─ uses → Product, ProductOption (entities)
  └─ throws → ProductNotFoundException

CartService
  ├─ depends on → CartRepositoryPort (interface)
  ├─ depends on → UserRepositoryPort (interface)
  ├─ uses → Cart, CartItem, User (entities)
  └─ throws → CartItemNotFoundException, InvalidQuantityException, UserNotFoundException

PopularProductServiceImpl
  ├─ depends on → ProductRepositoryPort (interface)
  └─ uses → Product (entity)
```

#### Domain (Port) ← Infrastructure (구현)
```
ProductRepositoryPort (interface)
  ↑ implemented by
InMemoryProductRepository (repository)
  └─ @Repository 등록 → Spring DI

CartRepositoryPort (interface)
  ↑ implemented by
InMemoryCartRepository (repository)
  └─ @Repository 등록 → Spring DI

UserRepositoryPort (interface)
  ↑ implemented by
InMemoryUserRepository (repository)
  └─ @Repository 등록 → Spring DI
```

### 3.3 의존성 주입 (DI) 흐름

```
Spring IoC Container
├─ @Repository 어노테이션 등록
│  ├─ InMemoryProductRepository → ProductRepositoryPort 구현체
│  ├─ InMemoryCartRepository → CartRepositoryPort 구현체
│  └─ InMemoryUserRepository → UserRepositoryPort 구현체
│
├─ @Service 어노테이션 등록
│  ├─ ProductService (ProductRepositoryPort 주입)
│  ├─ PopularProductServiceImpl (ProductRepositoryPort 주입)
│  └─ CartService (CartRepositoryPort, UserRepositoryPort 주입)
│
└─ @RestController 어노테이션 등록
   ├─ ProductController (ProductService 주입)
   ├─ PopularProductController (PopularProductService 주입)
   └─ CartController (CartService 주입)
```

---

## 4. 인메모리 DB 구현 분석

### 4.1 현재 구현 위치

```
Infrastructure Layer
└── infrastructure/persistence/
    ├── InMemoryProductRepository.java ✅
    ├── InMemoryCartRepository.java ✅
    └── InMemoryUserRepository.java ✅
```

**평가**: 위치가 적절합니다. Infrastructure 계층에 데이터 접근 계층이 위치해야 하는 클린 아키텍처 원칙을 따르고 있습니다.

### 4.2 인메모리 저장소 상세 구조

#### InMemoryProductRepository

**데이터 구조**
```java
private final Map<Long, Product> products = new HashMap<>();
private final Map<Long, ProductOption> productOptions = new HashMap<>();
private final Map<Long, List<Long>> productToOptionsMap = new HashMap<>();
private final Map<Long, Long> orderCount3DaysMap = new HashMap<>();
```

**저장된 샘플 데이터**
```
상품 (10개):
ID  이름         가격    재고상태
1   티셔츠      29,900원  판매중
2   청바지      79,900원  판매중
3   슬리퍼      19,900원  판매중
4   후드집업    49,900원  판매중
5   치마        39,900원  판매중
6   운동화      69,900원  판매중
7   스카프      24,900원  품절
8   모자        34,900원  판매중
9   장갑        19,900원  판매중지
10  양말        9,900원   판매중

옵션 (7개, 1, 2, 3번 상품에만 연결):
상품1: 101-블랙/M, 102-블랙/L, 103-화이트/M
상품2: 201-청색/32, 202-청색/34
상품3: 301-검정/260mm, 302-흰색/260mm

최근 3일 주문 수 (샘플):
상품1: 150, 상품2: 120, 상품3: 180...
(실제 환경에서는 Order 테이블에서 동적 계산)
```

**포트 구현 메서드**
```java
findAll()                      → 모든 상품 반환
findById(Long)                 → Optional<Product>
findOptionsByProductId(Long)   → List<ProductOption>
findOptionById(Long)           → Optional<ProductOption>
getOrderCount3Days(Long)       → Long (인기상품 계산용)
save(Product)                  → 상품 저장
saveOption(ProductOption)      → 옵션 저장
```

#### InMemoryCartRepository

**데이터 구조**
```java
private final ConcurrentHashMap<Long, Cart> carts = new ConcurrentHashMap<>();
private final ConcurrentHashMap<Long, CartItem> cartItems = new ConcurrentHashMap<>();
private final ConcurrentHashMap<Long, Long> userCartMap = new ConcurrentHashMap<>();
private final AtomicLong cartIdGenerator = new AtomicLong(0);
private final AtomicLong cartItemIdGenerator = new AtomicLong(0);
```

**저장된 샘플 데이터**
```
사용자별 장바구니:
사용자 100: 장바구니 1
  ├─ 아이템 1: 티셔츠 (옵션 101-블랙/M), 수량 2, 단가 29,900원, 소계 59,800원
  └─ 총액: 59,800원

사용자 101: 장바구니 2
  ├─ 아이템 2: 슬리퍼 (옵션 501), 수량 1, 단가 19,900원, 소계 19,900원
  └─ 총액: 19,900원
```

**포트 구현 메서드**
```java
findOrCreateByUserId(Long)     → 장바구니 조회 또는 생성
findByUserId(Long)             → Optional<Cart>
findCartItemById(Long)         → Optional<CartItem>
saveCartItem(CartItem)         → CartItem (저장 후 반환)
deleteCartItem(Long)           → void
saveCart(Cart)                 → Cart (저장 후 반환)
getCartItems(Long cartId)      → List<CartItem> (추가 메서드)
```

**동시성 처리**
- `ConcurrentHashMap` 사용: 멀티스레드 환경에서 안전
- `AtomicLong` 사용: 스레드-세이프한 ID 생성기

### 4.3 향후 DB 전환 계획

**현재 (메모리 기반)**
```
InMemoryProductRepository → HashMap → 메모리
InMemoryCartRepository    → ConcurrentHashMap → 메모리
InMemoryUserRepository    → HashMap → 메모리
```

**향후 (JPA/MySQL 기반)**
```
ProductRepositoryPort
  ├─ InMemoryProductRepository (현재)
  └─ JpaProductRepository (향후) → MySQL

CartRepositoryPort
  ├─ InMemoryCartRepository (현재)
  └─ JpaCartRepository (향후) → MySQL

UserRepositoryPort
  ├─ InMemoryUserRepository (현재)
  └─ JpaUserRepository (향후) → MySQL
```

**포트 인터페이스는 변경 없음** (의존성 역전의 이점)
- Application 계층은 구현체 변경을 모름
- Controller도 변경 불필요
- `@Repository` 어노테이션만 다른 구현체로 교체

---

## 5. DTO, Repository, Service, Controller 관계

### 5.1 요청-응답 흐름도

```
┌─────────────────────────────────────────────────────────────┐
│                     Client Request                          │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│          Presentation Layer (Controller)                    │
│  CartController.addCartItem()                               │
│  ├─ @RequestHeader("X-USER-ID") Long userId                │
│  └─ @RequestBody AddCartItemRequest request                │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│        Application Layer (Service)                          │
│  CartService.addItem(userId, request)                       │
│  ├─ 사용자 존재 검증 (UserRepositoryPort)                  │
│  ├─ 수량 검증                                               │
│  ├─ 장바구니 조회 또는 생성 (CartRepositoryPort)           │
│  └─ CartItem 생성 및 저장                                   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│    Infrastructure Layer (Repository)                        │
│  InMemoryCartRepository.saveCartItem()                      │
│  ├─ CartItem 저장 (ConcurrentHashMap)                       │
│  └─ AtomicLong으로 ID 생성                                  │
│                                                              │
│  InMemoryUserRepository.existsById()                        │
│  └─ HashMap에서 사용자 존재 확인                            │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│         Domain Layer (Entity, Port)                         │
│  CartItem, Cart, User (entities)                            │
│  CartRepositoryPort, UserRepositoryPort (interfaces)        │
└─────────────────────────────────────────────────────────────┘
                            ↓
                        (데이터)
                            ↑
┌─────────────────────────────────────────────────────────────┐
│    Infrastructure Layer (Repository)                        │
│  InMemoryCartRepository.saveCartItem()                      │
│  └─ CartItem 저장 완료, 생성된 객체 반환                   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│        Application Layer (Service)                          │
│  CartService.addItem()                                      │
│  ├─ CartItem → CartItemResponse 변환                       │
│  └─ 데이터 + 추가 정보(상품명, 옵션명) 포함               │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│          Presentation Layer (Controller)                    │
│  CartItemResponse return                                     │
│  └─ ResponseEntity.status(201 Created)                     │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   Client Response                           │
│  201 Created                                                 │
│  {cartItemId, cartId, productId, optionId, productName...} │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 상품 조회 흐름 (ProductService)

```
Client Request: GET /products?page=0&size=10&sort=product_id,desc
        ↓
ProductController.getProductList(page, size, sort)
        ↓
ProductService.getProductList(0, 10, "product_id,desc")
        │
        ├─ 파라미터 검증
        │  └─ page >= 0, 1 <= size <= 100
        │
        ├─ Repository에서 데이터 조회
        │  └─ ProductRepositoryPort.findAll()
        │     └─ InMemoryProductRepository.findAll()
        │        └─ HashMap에서 모든 상품 반환
        │
        ├─ 정렬 적용
        │  └─ Comparator를 이용한 메모리 정렬
        │
        ├─ 페이지네이션 계산
        │  └─ startIndex, endIndex 계산
        │
        └─ DTO 변환 (Product → ProductResponse)
           └─ List<ProductResponse> 생성

Response: ProductListResponse {
  content: [ProductResponse...],
  totalElements: 10,
  totalPages: 1,
  currentPage: 0,
  size: 10
}
```

### 5.3 계층 간 데이터 흐름

```
Presentation Layer:
  ├─ Request DTO: AddCartItemRequest (요청 파라미터)
  └─ Response DTO: CartItemResponse (응답 바디)

Application Layer:
  ├─ Input: AddCartItemRequest
  ├─ Process: 비즈니스 로직
  ├─ Domain Entities 사용: Cart, CartItem, User
  └─ Output: CartItemResponse

Infrastructure Layer:
  ├─ Input: CartItem (entity)
  ├─ Process: 저장소 조회/저장
  ├─ Storage: ConcurrentHashMap
  └─ Output: CartItem (저장된 entity)

Domain Layer:
  ├─ Entities: Cart, CartItem, User...
  ├─ Ports: CartRepositoryPort, UserRepositoryPort...
  └─ Exceptions: CartItemNotFoundException...
```

### 5.4 DTO와 Entity의 분리

**좋은 예: CartResponseDto**
```java
// Presentation Layer DTO
CartResponseDto {
  cartId: Long
  userId: Long
  totalItems: Integer
  totalPrice: Long
  items: List<CartItemResponse>  // 중첩 DTO
  updatedAt: LocalDateTime
}

// Domain Layer Entity
Cart {
  cartId: Long
  userId: Long
  totalItems: Integer (계산 필드)
  totalPrice: Long (계산 필드)
  createdAt: LocalDateTime
  updatedAt: LocalDateTime
}
```
**분리 이유**:
- Entity는 도메인 규칙 포함 (totalItems, totalPrice는 계산됨)
- DTO는 API 응답 포맷만 포함
- 필요시 DTO에만 필드 추가 가능 (Entity 변경 없음)

**우려사항: CartService의 하드코딩된 데이터**
```java
// ⚠️ 문제: 상품명, 옵션명, 가격을 switch 문으로 하드코딩
private String getProductName(Long productId) {
  return switch (productId.intValue()) {
    case 1 -> "티셔츠";
    case 2 -> "청바지";
    ...
  };
}
```
**개선 방안**: ProductRepository 주입받아 동적으로 조회

---

## 6. 아키텍처 평가

### 6.1 4계층 아키텍처 준수도

| 계층 | 위치 | 파일수 | 역할 | 평가 |
|------|------|--------|------|------|
| **Presentation** | `presentation/*` | 14 | HTTP 요청/응답 처리 | 🟡 부분 (컨트롤러 위치 불일치) |
| **Application** | `application/*` | 4 | 비즈니스 로직 | 🟡 부분 (서비스 위치 불일치) |
| **Domain** | `domain/*` | 16 | 비즈니스 규칙, 엔티티 | ✅ 우수 |
| **Infrastructure** | `infrastructure/*` | 3 | 데이터 접근 구현 | ✅ 우수 |

### 6.2 의존성 역전 (Dependency Inversion) 평가

```
✅ Domain은 Infrastructure에 의존하지 않음
✅ ProductRepositoryPort 인터페이스로 추상화
✅ InMemoryProductRepository가 포트 구현
✅ Application은 포트를 통해 접근
✅ Spring @Repository로 자동 주입

결과: 클린 아키텍처 원칙 잘 준수 ✅
```

### 6.3 코드 품질 평가

| 항목 | 현황 | 점수 | 평가 |
|------|------|------|------|
| **아키텍처 설계** | 계층이 명확히 분리됨 | 8/10 | 우수 (위치 불일치만 개선 필요) |
| **의존성 관리** | 포트-어댑터 패턴 적용 | 9/10 | 우수 |
| **코드 조직** | 대부분 적절히 정리됨 | 7/10 | 중상 (일부 위치 개선 필요) |
| **테스트 가능성** | 의존성 주입으로 테스트 용이 | 8/10 | 우수 |
| **유지보수성** | 명확한 책임 분리 | 8/10 | 우수 |
| **확장성** | DB 전환 용이한 구조 | 9/10 | 우수 |
| **에러 처리** | 글로벌 예외 핸들러 적용 | 7/10 | 중상 (도메인 예외 잘 정의됨) |
| **데이터 안전성** | 메모리 기반 (영속성 부족) | 3/10 | 낮음 (임시 구현) |

### 6.4 강점

```
✅ 명확한 4계층 분리
  - Presentation, Application, Domain, Infrastructure이 구분됨

✅ 포트-어댑터 패턴 적용
  - ProductRepositoryPort, CartRepositoryPort 등 인터페이스로 추상화
  - 구현체(InMemory)를 쉽게 교체 가능 (향후 JPA로 전환 용이)

✅ 의존성 역전 원칙 준수
  - Domain이 Infrastructure에 의존하지 않음
  - 의존성이 한 방향으로 흐름

✅ 도메인 주도 설계 부분 적용
  - 도메인 엔티티가 명확히 정의됨 (Product, Cart, User...)
  - 도메인 예외가 분리됨 (CartItemNotFoundException...)

✅ 전역 예외 처리
  - GlobalExceptionHandler로 일관된 에러 응답

✅ DTO와 Entity 분리
  - Presentation 계층이 고유한 DTO 사용
  - Domain entity 변경 시 API 영향 최소화

✅ 샘플 데이터 초기화
  - 10개 상품, 2개 사용자 샘플 데이터로 테스트 용이
```

### 6.5 약점

```
⚠️ 패키지 구조 불일치
  - ProductController, PopularProductController가 presentation/ 루트에 위치
  - ProductService, PopularProductServiceImpl이 application/ 루트에 위치
  - 추천: controller/, service/ 서브패키지로 통일

⚠️ 인터페이스 부족
  - ProductService는 인터페이스 없이 구현체만 존재
  - PopularProductService는 있지만 ProductService와 불일치
  - 추천: 모든 Service를 인터페이스 + 구현체로 분리

⚠️ 하드코딩된 샘플 데이터
  - CartService의 getProductName(), getOptionName(), getProductPrice()
  - 상품명, 옵션명, 가격이 switch 문으로 하드코딩됨
  - 추천: ProductRepository에서 동적으로 조회

⚠️ 메모리 기반 저장소
  - 애플리케이션 재시작 시 모든 데이터 소실
  - 프로덕션 환경 부적합
  - 추천: 향후 MySQL + JPA로 전환 필수

⚠️ 낮은 테스트 커버리지
  - 전체 40% 정도만 테스트됨
  - 미구현 기능(Order, Coupon, Inventory)에 테스트만 존재

⚠️ 미완성 기능
  - Order, Coupon, Inventory 엔티티는 정의되었지만 Service/Controller 미구현
  - 해당 테스트 파일만 존재 (테스트가 실제 기능을 검증하지 못함)

⚠️ 캐싱 구현의 한계
  - ConcurrentHashMap 기반 수동 캐싱
  - TTL 관리가 복잡
  - 추천: Spring Cache, Redis 사용

⚠️ 트랜잭션 관리 부재
  - 메모리 기반이라 트랜잭션 미적용
  - DB 전환 시 @Transactional 추가 필요
```

---

## 7. 주요 발견사항 및 개선사항

### 7.1 즉시 개선 필요 (우선순위: 높음)

#### 1️⃣ 패키지 구조 표준화

**현재 상황**
```
application/
├── ProductService.java (루트)
├── PopularProductService.java (루트)
├── PopularProductServiceImpl.java (루트)
└── service/
    └── CartService.java
```

**문제점**
- ProductController와 ProductService 위치가 불일치
- 3가지 Service의 패키지 위치가 다름
- 신규 서비스 추가 시 위치 결정 어려움

**개선 방안**
```
application/
└── service/
    ├── ProductService.java (인터페이스 추가)
    ├── ProductServiceImpl.java (새로 생성)
    ├── PopularProductService.java
    ├── PopularProductServiceImpl.java
    └── CartService.java
```

**영향도**: 낮음 (파일 이동만, 로직 변경 없음)

---

#### 2️⃣ CartService 개선: 하드코딩된 데이터 제거

**현재 문제**
```java
// CartService.java의 문제점
private String getProductName(Long productId) {
  return switch (productId.intValue()) {
    case 1 -> "티셔츠";
    case 2 -> "청바지";
    ...
  };
}
```

**문제점**
- 새로운 상품 추가 시 코드 수정 필요
- 상품명이 변경되면 이 코드도 수정 필요
- 유지보수 어려움
- 확장성 부족

**개선 방안**
```java
// ProductRepositoryPort 주입
private final ProductRepositoryPort productRepository;

// CartService 생성자에 추가
public CartService(CartRepositoryPort cartRepository,
                  UserRepositoryPort userRepository,
                  ProductRepositoryPort productRepository) {  // 추가
  this.cartRepository = cartRepository;
  this.userRepository = userRepository;
  this.productRepository = productRepository;  // 추가
}

// 메서드 개선
private String getProductName(Long productId) {
  return productRepository.findById(productId)
    .map(Product::getProductName)
    .orElse("상품" + productId);
}
```

**영향도**: 중간 (Repository 주입 추가, 로직 개선)

---

#### 3️⃣ ProductService 인터페이스 추가

**현재 상황**
```java
// application/ProductService.java
public class ProductService {  // 인터페이스 없음
  ...
}
```

**문제점**
- PopularProductService는 인터페이스가 있는데 ProductService는 없음
- 테스트 시 목(Mock) 객체 생성 어려움
- 일관성 부족

**개선 방안**
```java
// application/service/ProductService.java (인터페이스)
public interface ProductService {
  ProductListResponse getProductList(int page, int size, String sort);
  ProductDetailResponse getProductDetail(Long productId);
}

// application/service/ProductServiceImpl.java (구현체)
@Service
public class ProductServiceImpl implements ProductService {
  // 현재 코드 이동
}
```

**영향도**: 낮음 (리팩토링, 기능 변경 없음)

---

### 7.2 중기 개선 필요 (우선순위: 중간)

#### 4️⃣ 미구현 기능 완성

**Order 관리**
```
Status: ⚠️ 미구현
- Entity: ✅ Order, OrderItem 정의됨
- Repository: ❌ OrderRepositoryPort, InMemoryOrderRepository 미구현
- Service: ❌ OrderService 미구현
- Controller: ❌ OrderController 미구현
- Test: ⚠️ OrderControllerTest만 존재 (더미)

필요 작업:
1. OrderRepositoryPort 인터페이스 정의
2. InMemoryOrderRepository 구현
3. OrderService 구현 (주문 생성, 조회, 취소 등)
4. OrderController 구현
5. OrderControllerTest 실제 구현
```

**Coupon 관리**
```
Status: ⚠️ 미구현
- Entity: ✅ Coupon, UserCoupon 정의됨
- Repository: ❌ CouponRepositoryPort, InMemoryCouponRepository 미구현
- Service: ❌ CouponService 미구현
- Controller: ❌ CouponController 미구현
- Test: ⚠️ CouponControllerTest만 존재 (더미)

필요 작업:
1. CouponRepositoryPort 인터페이스 정의
2. InMemoryCouponRepository 구현
3. CouponService 구현 (쿠폰 적용, 검증 등)
4. CouponController 구현
5. CouponControllerTest 실제 구현
```

**Inventory 관리**
```
Status: ⚠️ 미구현
- Entity: ⚠️ ProductOption이 재고를 포함하지만 별도 관리 필요
- Repository: ❌ InventoryRepositoryPort, InMemoryInventoryRepository 미구현
- Service: ❌ InventoryService 미구현
- Controller: ❌ InventoryController 미구현
- Test: ⚠️ InventoryControllerTest만 존재 (더미)

필요 작업:
1. 재고 관리 전략 정의 (ProductOption vs 별도 Inventory)
2. InventoryRepositoryPort 정의
3. InMemoryInventoryRepository 구현
4. InventoryService 구현 (재고 감소, 복구 등)
5. InventoryController 구현
6. 낙관적 잠금(Optimistic Locking) 구현
```

**추정 공수**: 4~6주 (3개 기능 × 10~15일)

---

#### 5️⃣ 테스트 커버리지 확대

**현재 상황**
```
Total: ~40% 커버리지
- CartControllerTest: ✅ 존재
- PopularProductControllerTest: ✅ 존재
- PopularProductServiceTest: ✅ 존재
- CartItemNotFoundExceptionTest: ❌ 부재
- InvalidQuantityExceptionTest: ❌ 부재
- ProductServiceTest: ❌ 부재
- ProductControllerTest: ⚠️ 테스트만 존재
```

**개선 목표**: 70% 이상 커버리지

**추가 필요 테스트**
```
1. ProductService 단위 테스트 (10개 케이스)
   - 페이지네이션 테스트
   - 정렬 테스트
   - 예외 처리 테스트

2. CartService 단위 테스트 (12개 케이스)
   - 아이템 추가/수정/삭제
   - 사용자 검증
   - 수량 검증

3. 통합 테스트 (8개 케이스)
   - 전체 흐름 테스트
   - 데이터베이스 통합 테스트

추정 공수: 2~3주
```

---

#### 6️⃣ 데이터베이스 전환 준비

**현재**: InMemory (메모리)
**목표**: MySQL + Spring Data JPA

**단계별 계획**
```
Phase 1: JPA Entity 매핑 (1주)
  - @Entity, @Id, @Column 어노테이션 추가
  - @Table 매핑
  - @OneToMany, @ManyToOne 관계 정의
  - @Version (낙관적 잠금용)

Phase 2: JPA Repository 구현 (1주)
  - JpaRepository 상속
  - Custom 쿼리 구현 (getOrderCount3Days 등)
  - Named Query 정의

Phase 3: 설정 추가 (3일)
  - application.properties (MySQL 연결)
  - JpaConfig (Hibernate 설정)
  - DB 마이그레이션 스크립트

Phase 4: 테스트 및 전환 (1주)
  - @DataJpaTest로 Repository 테스트
  - 통합 테스트 실행
  - InMemory → JPA 전환

포트 인터페이스 변경 없음! ✅
=> Application 계층은 영향 없음
```

**추정 공수**: 4주

---

### 7.3 장기 개선 (우선순위: 낮음)

#### 7️⃣ 캐싱 전략 고도화

**현재**: ConcurrentHashMap + 수동 TTL 관리

**개선**
```java
// Spring Cache 사용
@Cacheable(value = "popularProducts", unless = "#result == null")
@CacheEvict(value = "popularProducts", allEntries = true, ...)
public PopularProductListResponse getPopularProducts() {
  ...
}

// 향후: Redis 도입
// - 분산 캐싱
// - 설정 기반 TTL 관리
// - 캐시 통계 수집
```

---

#### 8️⃣ 이벤트 소싱 (Event Sourcing)

**현재**: Outbox 엔티티만 정의, 미구현

**개선**
```
주문 생성 → OrderEvent 발행
          → Outbox에 저장
          → 비동기로 처리
          → 이메일 발송, 재고 감소 등

OutboxEvent Entity 정의
OutboxEventRepository 구현
OutboxService 구현 (이벤트 발행/처리)
```

---

#### 9️⃣ 마이크로서비스 분리

**현재 모놀리식 구조**
```
e-commerce (모놀리식)
├── Product Service
├── Cart Service
├── Order Service
└── Coupon Service
```

**향후 마이크로서비스**
```
product-service/
  ├── ProductService
  └── InventoryService

cart-service/
  └── CartService

order-service/
  ├── OrderService
  └── PaymentService

coupon-service/
  └── CouponService

공통 라이브러리/
  ├── domain/
  ├── exceptions/
  └── utils/
```

---

### 7.4 개선 로드맵 (우선순위순)

```
Week 1-2: 즉시 개선
  ✅ 패키지 구조 표준화
  ✅ ProductService 인터페이스 추가
  ✅ CartService 개선 (하드코딩 제거)

Week 3-4: 중기 개선
  ✅ 테스트 커버리지 확대 (40% → 70%)
  ✅ 미구현 기능 1차 (Order)

Week 5-8: 중기 개선 계속
  ✅ 미구현 기능 2차 (Coupon)
  ✅ 미구현 기능 3차 (Inventory)
  ✅ 데이터베이스 전환 (MySQL + JPA)

Week 9+: 장기 개선
  ✅ 캐싱 고도화 (Redis)
  ✅ 이벤트 소싱
  ✅ 마이크로서비스 분리

총 추정 공수: 2-3개월 (1인 개발)
```

---

## 📊 아키텍처 종합 평가

### 최종 점수

| 항목 | 점수 | 비고 |
|------|------|------|
| **아키텍처 설계** | 8/10 | 클린 아키텍처 잘 준수 |
| **코드 구조** | 7/10 | 일부 위치 불일치 |
| **의존성 관리** | 9/10 | 포트-어댑터 패턴 우수 |
| **테스트 가능성** | 7/10 | 기본 커버리지 달성 |
| **확장성** | 8/10 | DB 전환 용이 |
| **유지보수성** | 7/10 | 기본 수준 |
| **기능 완성도** | 5/10 | 일부 미구현 |
| **데이터 지속성** | 2/10 | 메모리 기반 (임시) |
| **프로덕션 준비도** | 4/10 | 개선 필요 |
| **종합 평가** | **6.8/10** | **성장 단계 프로젝트** |

---

## 🎯 결론

### 현재 상태
HH+ e-commerce 프로젝트는 **클린 아키텍처의 기본을 잘 따르는 견고한 초기 단계 프로젝트**입니다.

### 강점
- ✅ 4계층 명확한 분리
- ✅ 포트-어댑터 패턴 적용으로 의존성 역전 달성
- ✅ 테스트 가능한 구조
- ✅ DB 전환 용이한 설계

### 주요 개선 과제
1. 📁 패키지 구조 표준화 (1주)
2. 🔧 미구현 기능 완성 (5주)
3. 🧪 테스트 커버리지 확대 (2주)
4. 💾 데이터베이스 통합 (4주)

### 추천 단계별 개선 전략
```
단계 1 (1-2주): 구조 개선
  → 패키지 위치 정리
  → 인터페이스 추가
  → 하드코딩 제거

단계 2 (3-4주): 기능 완성
  → Order, Coupon, Inventory 구현
  → 테스트 커버리지 70% 이상

단계 3 (5-8주): 데이터베이스 전환
  → MySQL + Spring Data JPA 도입
  → 프로덕션 환경 준비

단계 4 (9주+): 고도화
  → Redis 캐싱
  → 이벤트 소싱
  → 마이크로서비스 분리 검토
```

### 최종 의견
**지금까지의 설계는 훌륭합니다. 위의 개선사항들을 순서대로 진행하면 프로덕션 레벨의 견고한 e-commerce 플랫폼으로 발전할 수 있을 것입니다.**

---

**문서 작성 완료**
**분석 기준**: Spring Boot 3.5.7, Java 17, Layered Architecture
**마지막 업데이트**: 2025-11-06
