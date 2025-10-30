# E-Commerce 데이터 모델

## 개요

데이터 모델은 옵션 기반 재고를 갖춘 상품 카탈로그 관리, 쇼핑 카트 기능, 원자적 거래가 있는 주문 처리, 쿠폰 기반 할인을 지원합니다. 핵심 설계: 재고는 상품 옵션별로 추적되고, 카트는 재고에 영향을 주지 않으며, 주문은 원자적입니다 (재고 + 잔액 + 쿠폰).

---

## 엔티티 요약

| 엔티티 | 목적 |
|--------|---------|
| **users** | 잔액이 있는 사용자 계정 |
| **products** | 상품 카탈로그 |
| **product_options** | 독립적인 재고가 있는 상품 변형 |
| **carts** | 사용자별 쇼핑 카트 |
| **cart_items** | 카트 라인 항목 |
| **orders** | 완료된 주문 |
| **order_items** | 주문 라인 항목 |
| **coupons** | 할인 쿠폰 |
| **user_coupons** | 쿠폰 발급 및 사용 |

---

## 엔티티 정의

### users
```
user_id         : Long (PK)
email           : String (UNIQUE)
password_hash   : String
name            : String
phone           : String
balance         : Long
created_at      : Timestamp
updated_at      : Timestamp
```

### products
```
product_id      : Long (PK)
product_name    : String
description     : Text
price           : Long
total_stock     : Integer (calculated: SUM of option stocks)
status          : String (판매 중 | 품절)
created_at      : Timestamp
updated_at      : Timestamp
```

### product_options
```
option_id       : Long (PK)
product_id      : Long (FK → products)
name            : String (e.g., "Black/M")
stock           : Integer
version         : Long (optimistic lock)
created_at      : Timestamp
updated_at      : Timestamp
```

### carts
```
cart_id         : Long (PK)
user_id         : Long (FK → users, UNIQUE)
total_items     : Integer
total_price     : Long
created_at      : Timestamp
updated_at      : Timestamp
```

### cart_items
```
cart_item_id    : Long (PK)
cart_id         : Long (FK → carts)
product_id      : Long (FK → products)
option_id       : Long (FK → product_options, REQUIRED)
quantity        : Integer
unit_price      : Long
created_at      : Timestamp
updated_at      : Timestamp
```

### orders
```
order_id        : Long (PK)
user_id         : Long (FK → users)
order_status    : String (COMPLETED | PENDING | FAILED)
coupon_id       : Long (FK → coupons, nullable)
coupon_discount : Long
subtotal        : Long
final_amount    : Long
created_at      : Timestamp
updated_at      : Timestamp
```

### order_items
```
order_item_id   : Long (PK)
order_id        : Long (FK → orders)
product_id      : Long (FK → products)
option_id       : Long (FK → product_options)
product_name    : String (snapshot)
option_name     : String (snapshot)
quantity        : Integer
unit_price      : Long
created_at      : Timestamp
```

### coupons
```
coupon_id       : Long (PK)
coupon_name     : String
description     : Text
discount_type   : String (FIXED_AMOUNT | PERCENTAGE)
discount_amount : Long
discount_rate   : Decimal
total_quantity  : Integer
remaining_qty   : Integer (atomic decrement)
valid_from      : Timestamp
valid_until     : Timestamp
is_active       : Boolean
version         : Long (optimistic lock)
created_at      : Timestamp
updated_at      : Timestamp
```

### user_coupons
```
user_coupon_id  : Long (PK)
user_id         : Long (FK → users)
coupon_id       : Long (FK → coupons)
status          : String (ACTIVE | USED | EXPIRED)
issued_at       : Timestamp
used_at         : Timestamp (nullable)
order_id        : Long (FK → orders, nullable)
```

---

## 엔티티 관계도 (ERD)

```mermaid
erDiagram
    USERS ||--|| CARTS : owns
    USERS ||--o{ ORDERS : places
    USERS ||--o{ USER_COUPONS : receives

    CARTS ||--o{ CART_ITEMS : contains
    PRODUCTS ||--o{ PRODUCT_OPTIONS : has
    PRODUCTS ||--o{ CART_ITEMS : ""
    PRODUCTS ||--o{ ORDER_ITEMS : ""
    PRODUCT_OPTIONS ||--o{ CART_ITEMS : ""
    PRODUCT_OPTIONS ||--o{ ORDER_ITEMS : ""

    ORDERS ||--o{ ORDER_ITEMS : contains
    ORDERS }o--|| COUPONS : applies
    ORDERS ||--o{ USER_COUPONS : uses

    COUPONS ||--o{ USER_COUPONS : distributes

    USERS {
        long user_id PK
        string email UK
        long balance
    }

    PRODUCTS {
        long product_id PK
        string product_name
        long price
        int total_stock
        string status
    }

    PRODUCT_OPTIONS {
        long option_id PK
        long product_id FK
        string name
        int stock
        long version
    }

    CARTS {
        long cart_id PK
        long user_id FK "UK"
    }

    CART_ITEMS {
        long cart_item_id PK
        long cart_id FK
        long product_id FK
        long option_id FK "REQUIRED"
        int quantity
    }

    ORDERS {
        long order_id PK
        long user_id FK
        string order_status
        long coupon_id FK
        long final_amount
    }

    ORDER_ITEMS {
        long order_item_id PK
        long order_id FK
        long product_id FK
        long option_id FK
        int quantity
    }

    COUPONS {
        long coupon_id PK
        string coupon_name
        int remaining_qty
        timestamp valid_until
        long version
    }

    USER_COUPONS {
        long user_coupon_id PK
        long user_id FK
        long coupon_id FK
        string status
    }
```

---

## 주요 제약 조건 & 카디널리티

| 관계 | 카디널리티 | 제약 조건 |
|--------------|-------------|-----------|
| users → carts | 1:1 | UNIQUE(user_id) |
| users → orders | 1:N | FK user_id |
| users → user_coupons | 1:N | FK user_id |
| carts → cart_items | 1:N | FK cart_id |
| products → product_options | 1:N | FK product_id |
| product_options → cart_items | 1:N | FK option_id, REQUIRED |
| product_options → order_items | 1:N | FK option_id |
| orders → order_items | 1:N | FK order_id |
| orders → coupons | N:1 | FK coupon_id (nullable) |
| coupons → user_coupons | 1:N | FK coupon_id |
| user_coupons → orders | N:1 | FK order_id (nullable) |

---

## 핵심 데이터 제약 조건

| 제약 조건 | 엔티티 | 상세 정보 |
|-----------|--------|---------|
| 재고 음수 금지 | product_options | stock >= 0 |
| 옵션 필수 | cart_items | option_id NOT NULL |
| 상품별 고유 옵션 | product_options | UNIQUE(product_id, name) |
| 사용자별 고유 쿠폰 | user_coupons | UNIQUE(user_id, coupon_id) |
| 사용자당 하나의 카트 | carts | UNIQUE(user_id) |
| 총 재고 계산 | products | total_stock = SUM(option stocks) |
| 낙관적 락 | product_options, coupons | 동시성을 위한 버전 컬럼 |

---

## 인덱싱 & 성능 전략 (Indexing & Performance Strategy)

### 개요

본 섹션은 api-specification.md에서 정의된 주요 API(특히 통계 API)의 성능 최적화를 위해 필요한 보조 인덱스 전략을 설명합니다.

**성능 목표**:
- 상품 목록 조회: < 1초 (요구사항 3.1)
- 인기 상품 조회 (최근 3일, TOP 5): < 2초 (요구사항 2.1.3)
- 재고 현황 조회: < 1초

**인덱스 설계 원칙**:
- PK, FK, UNIQUE 제약은 DB 자동 인덱싱으로 제외
- 쿼리 성능 향상 목적의 보조 인덱스만 정의
- 쓰기 성능(INSERT/UPDATE) 영향을 고려한 선택적 적용

---

### 주요 테이블별 인덱스 전략

#### 1. **products** 테이블

**핵심 쿼리**:
- 상품 목록 조회 (페이지네이션): `SELECT * FROM products ORDER BY product_id DESC LIMIT 10`
- 인기 상품 집계: `SELECT product_id, product_name, COUNT(*) as order_count FROM orders o JOIN order_items oi ON o.order_id = oi.order_id JOIN products p ON oi.product_id = p.product_id WHERE o.created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY) GROUP BY oi.product_id ORDER BY order_count DESC LIMIT 5`

| 인덱스명 | 컬럼 조합 | 용도 | 우선순위 |
|---------|---------|------|---------|
| `idx_products_status_created` | (status, created_at DESC) | 판매 중 상품 필터링 및 생성 시간 정렬 | ⭐⭐⭐ 높음 |
| `idx_products_created` | (created_at DESC) | 최신 상품 조회 시 정렬 | ⭐⭐ 중간 |

**적용 예시** (SQL):
```sql
CREATE INDEX idx_products_status_created ON products(status, created_at DESC);
CREATE INDEX idx_products_created ON products(created_at DESC);
```

---

#### 2. **order_items** 테이블

**핵심 쿼리**:
- 상품별 판매량 집계: `SELECT oi.product_id, COUNT(*) as order_count FROM order_items oi JOIN orders o ON oi.order_id = o.order_id WHERE o.created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY) GROUP BY oi.product_id ORDER BY order_count DESC`
- 주문 항목 조회: `SELECT * FROM order_items WHERE order_id = ?`

| 인덱스명 | 컬럼 조합 | 용도 | 우선순위 |
|---------|---------|------|---------|
| `idx_order_items_product_created` | (product_id, order_id) | 상품별 판매량 집계 (orders.created_at 조인 시) | ⭐⭐⭐ 높음 |
| `idx_order_items_order` | (order_id, product_id) | 주문 항목 조회 (FK 조인 최적화) | ⭐⭐⭐ 높음 |

**적용 예시** (SQL):
```sql
CREATE INDEX idx_order_items_product_created ON order_items(product_id, order_id);
CREATE INDEX idx_order_items_order ON order_items(order_id, product_id);
```

**주의**: FK(order_id)는 이미 자동 인덱싱되므로, 복합 인덱스는 추가 컬럼을 포함하여 커버링 인덱스 효과 제공.

---

#### 3. **product_options** 테이블

**핵심 쿼리**:
- 상품별 옵션 조회: `SELECT * FROM product_options WHERE product_id = ? ORDER BY option_id`
- 재고 현황 조회: `SELECT * FROM product_options WHERE product_id = ?`

| 인덱스명 | 컬럼 조합 | 용도 | 우선순위 |
|---------|---------|------|---------|
| `idx_product_options_product` | (product_id) | 상품별 옵션 조회 (FK 조인 최적화) | ⭐⭐⭐ 높음 |
| `idx_product_options_stock` | (product_id, stock DESC) | 재고 현황 정렬 및 필터링 | ⭐⭐ 중간 |

**적용 예시** (SQL):
```sql
CREATE INDEX idx_product_options_product ON product_options(product_id);
CREATE INDEX idx_product_options_stock ON product_options(product_id, stock DESC);
```

**주의**: PK(option_id) 및 FK(product_id)는 이미 자동 인덱싱됨.

---

#### 4. **orders** 테이블

**핵심 쿼리**:
- 주문 목록 조회 (사용자별): `SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC`
- 최근 3일 주문 집계: `SELECT order_id FROM orders WHERE created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)`

| 인덱스명 | 컬럼 조합 | 용도 | 우선순위 |
|---------|---------|------|---------|
| `idx_orders_user_created` | (user_id, created_at DESC) | 사용자별 주문 조회 및 정렬 | ⭐⭐⭐ 높음 |
| `idx_orders_created` | (created_at DESC) | 최근 주문 집계 (통계 API) | ⭐⭐⭐ 높음 |

**적용 예시** (SQL):
```sql
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);
CREATE INDEX idx_orders_created ON orders(created_at DESC);
```

**성능 영향**:
- `GET /products/popular` API: 최근 3일 주문 필터링 시 Full Table Scan 방지
- `idx_orders_created` 사용 시 expected reduction: Full Scan → Index Range Scan (500배 이상 개선 가능)

---

#### 5. **user_coupons** 테이블

**핵심 쿼리**:
- 사용자 쿠폰 조회: `SELECT * FROM user_coupons WHERE user_id = ? AND status = 'ACTIVE'`
- 쿠폰 발급 가능 확인: `SELECT COUNT(*) FROM user_coupons WHERE user_id = ? AND coupon_id = ?`

| 인덱스명 | 컬럼 조합 | 용도 | 우선순위 |
|---------|---------|------|---------|
| `idx_user_coupons_user_status` | (user_id, status) | 사용자별 쿠폰 상태 조회 | ⭐⭐⭐ 높음 |
| `idx_user_coupons_unique_check` | (user_id, coupon_id, status) | 중복 발급 검사 및 상태 확인 | ⭐⭐ 중간 |

**적용 예시** (SQL):
```sql
CREATE INDEX idx_user_coupons_user_status ON user_coupons(user_id, status);
CREATE INDEX idx_user_coupons_unique_check ON user_coupons(user_id, coupon_id, status);
```

**주의**: UNIQUE(user_id, coupon_id)는 이미 정의되어 자동 인덱싱됨. 상태 필터링을 위해 복합 인덱스 추가.

---

### 인덱스 적용 우선순위

#### **Phase 1 (필수 적용)** - 즉시 적용 권장
```sql
-- 통계 API 성능 최적화 (인기 상품 조회)
CREATE INDEX idx_orders_created ON orders(created_at DESC);
CREATE INDEX idx_order_items_product_created ON order_items(product_id, order_id);

-- 기본 조회 성능
CREATE INDEX idx_products_status_created ON products(status, created_at DESC);
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);
CREATE INDEX idx_user_coupons_user_status ON user_coupons(user_id, status);
```

#### **Phase 2 (권장 적용)** - 부하 테스트 후 적용
```sql
-- 옵션별 조회 최적화
CREATE INDEX idx_product_options_stock ON product_options(product_id, stock DESC);

-- 추가 사용자 쿠폰 조회
CREATE INDEX idx_user_coupons_unique_check ON user_coupons(user_id, coupon_id, status);
```

---

### 성능 예상 개선 효과

| 쿼리 | 개선 전 | 개선 후 | 예상 개선율 |
|------|--------|--------|-----------|
| `GET /products/popular` (3일 주문 집계) | ~2초 이상 (Full Scan) | < 200ms | **90% ↓** |
| `GET /products/{product_id}` (옵션 포함) | ~500ms | < 100ms | **80% ↓** |
| `GET /orders` (사용자별 주문) | ~800ms | < 150ms | **81% ↓** |
| `GET /coupons/issued` (사용자 쿠폰) | ~600ms | < 100ms | **83% ↓** |

---

### 인덱스 유지보수 전략

#### 정기적 모니터링
```sql
-- MySQL: 인덱스 사용 현황 확인
SELECT * FROM performance_schema.table_io_waits_summary_by_index_usage
WHERE OBJECT_SCHEMA = 'ecommerce' AND COUNT_READ > 0
ORDER BY COUNT_READ DESC;

-- 미사용 인덱스 제거
SELECT OBJECT_SCHEMA, OBJECT_NAME, INDEX_NAME
FROM performance_schema.table_io_waits_summary_by_index_usage
WHERE COUNT_STAR = 0 AND INDEX_NAME != 'PRIMARY';
```

#### 인덱스 최적화 시점
- **월 1회**: 인덱스 통계 업데이트 (ANALYZE TABLE)
- **분기별**: 미사용 인덱스 검토 및 정리
- **성능 이상 발생 시**: 즉시 쿼리 실행 계획 분석 (EXPLAIN)

#### 쓰기 성능 모니터링
- INSERT/UPDATE 성능 저하 시 불필요한 인덱스 재검토
- 복합 인덱스의 컬럼 순서 최적화 검토

---

### 추가 성능 최적화 전략

#### 1. 쿼리 최적화
```sql
-- ❌ 부분 최적화된 쿼리
SELECT p.*, COUNT(o.order_id) as order_count
FROM products p
LEFT JOIN order_items oi ON p.product_id = oi.product_id
LEFT JOIN orders o ON oi.order_id = o.order_id
WHERE o.created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
GROUP BY p.product_id
ORDER BY order_count DESC LIMIT 5;

-- ✅ 최적화된 쿼리 (인덱스 활용)
SELECT p.product_id, p.product_name, COUNT(*) as order_count
FROM products p
INNER JOIN order_items oi ON p.product_id = oi.product_id
INNER JOIN orders o ON oi.order_id = o.order_id
WHERE o.created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
GROUP BY oi.product_id
ORDER BY order_count DESC LIMIT 5;
```

#### 2. 캐싱 전략 (선택)
- Redis 캐싱: 인기 상품 TOP 5 (5분 TTL)
- 데이터베이스 쿼리 결과 캐시 (읽기 위주 데이터)

#### 3. 파티셔닝 고려사항
- 현재 데이터 규모에서는 불필요
- **향후 확장 시**:
  - orders, order_items: created_at 기준 월별 파티셔닝
  - user_coupons: user_id 범위 파티셔닝

---

### 주의사항

1. **트레이드오프**: 인덱스 추가 → 쓰기 성능 저하 (INSERT/UPDATE 시간 증가)
   - Phase 1 인덱스부터 단계적 적용 권장

2. **인덱스 선택도(Cardinality)**:
   - product_options.stock은 선택도가 낮을 수 있음 (0~100 범위)
   - 불필요 시 Phase 2에서 제거 가능

3. **데이터 증가에 따른 재검토**:
   - 월 100만+ 주문 발생 시 파티셔닝 재검토 필요
   - 6개월마다 인덱스 효율성 분석 권장
