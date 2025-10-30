# JSON Server Mock API 가이드

## 개요

이 가이드는 `db.json` 파일을 기반으로 JSON Server에서 e-commerce API를 모킹하는 방법을 설명합니다.

## 설정

### 1. JSON Server 설치

```bash
npm install -g json-server
# 또는 프로젝트 로컬 설치
npm install json-server --save-dev
```

### 2. 서버 실행

```bash
json-server --watch db.json --port 3000
```

**포트 변경**:
```bash
json-server --watch db.json --port 8080
```

**Base URL**: `http://localhost:3000/api` (라우팅 설정 필요 시)

---

## API 엔드포인트 매핑 및 요청 예시

### 1. 상품 조회 API

#### 1.1 상품 목록 조회

**Endpoint**: `GET /products`

**요청**:
```bash
# 모든 상품 조회
curl -X GET "http://localhost:3000/products"

# 페이지네이션 (JSON Server의 _page, _limit 파라미터 사용)
curl -X GET "http://localhost:3000/products?_page=1&_limit=10"

# 정렬 (_sort, _order)
curl -X GET "http://localhost:3000/products?_sort=product_id&_order=desc"
```

**응답 (200 OK)**:
```json
[
  {
    "product_id": 1,
    "product_name": "티셕츠",
    "description": "100% 면 티셔츠",
    "price": 29900,
    "total_stock": 100,
    "status": "판매 중",
    "created_at": "2025-10-29T10:00:00Z"
  },
  {
    "product_id": 2,
    "product_name": "청바지",
    "description": "고급 데님 청바지",
    "price": 79900,
    "total_stock": 80,
    "status": "판매 중",
    "created_at": "2025-10-29T10:05:00Z"
  }
]
```

---

#### 1.2 상품 상세 조회 (옵션 포함)

**Endpoint**: `GET /products/{product_id}`

**요청**:
```bash
curl -X GET "http://localhost:3000/products/1"
```

**응답 (200 OK)**:
```json
{
  "product_id": 1,
  "product_name": "티셔츠",
  "description": "100% 면 티셔츠",
  "price": 29900,
  "total_stock": 100,
  "status": "판매 중",
  "created_at": "2025-10-29T10:00:00Z"
}
```

**옵션 조회** (product_options 테이블):
```bash
# 상품 ID로 옵션 필터링
curl -X GET "http://localhost:3000/product_options?product_id=1"
```

**응답**:
```json
[
  {
    "option_id": 101,
    "product_id": 1,
    "name": "블랙/M",
    "stock": 30,
    "version": 5
  },
  {
    "option_id": 102,
    "product_id": 1,
    "name": "블랙/L",
    "stock": 25,
    "version": 5
  },
  {
    "option_id": 103,
    "product_id": 1,
    "name": "화이트/M",
    "stock": 45,
    "version": 3
  }
]
```

---

#### 1.3 인기 상품 조회 (TOP 5)

**Endpoint**: `GET /products/popular`

**요청**:
```bash
# JSON Server에서는 자동으로 정렬을 수동으로 처리해야 합니다
# 다음은 price 기준 내림차순으로 5개 조회
curl -X GET "http://localhost:3000/products?_sort=price&_order=desc&_limit=5"
```

---

### 2. 장바구니 API

#### 2.1 장바구니 조회

**Endpoint**: `GET /carts`

**요청**:
```bash
curl -X GET "http://localhost:3000/carts/1"
```

**응답 (200 OK)**:
```json
{
  "cart_id": 1,
  "user_id": 100,
  "total_items": 2,
  "total_price": 89700,
  "updated_at": "2025-10-29T12:30:00Z"
}
```

**장바구니 아이템 조회**:
```bash
# cart_id로 필터링
curl -X GET "http://localhost:3000/cart_items?cart_id=1"
```

**응답**:
```json
[
  {
    "cart_item_id": 1001,
    "cart_id": 1,
    "product_id": 1,
    "product_name": "티셔츠",
    "option_id": 101,
    "option_name": "블랙/M",
    "quantity": 2,
    "unit_price": 29900,
    "subtotal": 59800
  },
  {
    "cart_item_id": 1002,
    "cart_id": 1,
    "product_id": 5,
    "product_name": "슬리퍼",
    "option_id": 501,
    "option_name": "검정/260mm",
    "quantity": 1,
    "unit_price": 19900,
    "subtotal": 19900
  }
]
```

---

#### 2.2 장바구니 아이템 추가

**Endpoint**: `POST /cart_items`

**요청**:
```bash
curl -X POST "http://localhost:3000/cart_items" \
  -H "Content-Type: application/json" \
  -d '{
    "cart_id": 1,
    "product_id": 2,
    "product_name": "청바지",
    "option_id": 201,
    "option_name": "청색/32",
    "quantity": 1,
    "unit_price": 79900,
    "subtotal": 79900
  }'
```

**응답 (201 Created)**:
```json
{
  "cart_item_id": 1003,
  "cart_id": 1,
  "product_id": 2,
  "product_name": "청바지",
  "option_id": 201,
  "option_name": "청색/32",
  "quantity": 1,
  "unit_price": 79900,
  "subtotal": 79900
}
```

---

#### 2.3 장바구니 아이템 수량 수정

**Endpoint**: `PUT /cart_items/{cart_item_id}`

**요청**:
```bash
curl -X PUT "http://localhost:3000/cart_items/1001" \
  -H "Content-Type: application/json" \
  -d '{
    "quantity": 5,
    "subtotal": 149500
  }'
```

**응답 (200 OK)**:
```json
{
  "cart_item_id": 1001,
  "cart_id": 1,
  "product_id": 1,
  "product_name": "티셔츠",
  "option_id": 101,
  "option_name": "블랙/M",
  "quantity": 5,
  "unit_price": 29900,
  "subtotal": 149500
}
```

---

#### 2.4 장바구니 아이템 제거

**Endpoint**: `DELETE /cart_items/{cart_item_id}`

**요청**:
```bash
curl -X DELETE "http://localhost:3000/cart_items/1001"
```

**응답 (204 No Content)**:
```
(응답 본문 없음)
```

---

### 3. 주문 API

#### 3.1 주문 생성

**Endpoint**: `POST /orders`

**요청**:
```bash
curl -X POST "http://localhost:3000/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": 100,
    "order_status": "COMPLETED",
    "subtotal": 139700,
    "coupon_discount": 0,
    "coupon_id": null,
    "final_amount": 139700,
    "created_at": "2025-10-29T12:45:00Z"
  }'
```

**응답 (201 Created)**:
```json
{
  "order_id": 5004,
  "user_id": 100,
  "order_status": "COMPLETED",
  "subtotal": 139700,
  "coupon_discount": 0,
  "coupon_id": null,
  "final_amount": 139700,
  "created_at": "2025-10-29T12:45:00Z"
}
```

**주문 아이템 추가**:
```bash
curl -X POST "http://localhost:3000/order_items" \
  -H "Content-Type: application/json" \
  -d '{
    "order_id": 5004,
    "product_id": 1,
    "product_name": "티셔츠",
    "option_id": 101,
    "option_name": "블랙/M",
    "quantity": 2,
    "unit_price": 29900
  }'
```

---

#### 3.2 주문 상세 조회

**Endpoint**: `GET /orders/{order_id}`

**요청**:
```bash
curl -X GET "http://localhost:3000/orders/5001"
```

**응답 (200 OK)**:
```json
{
  "order_id": 5001,
  "user_id": 100,
  "order_status": "COMPLETED",
  "subtotal": 139700,
  "coupon_discount": 0,
  "coupon_id": null,
  "final_amount": 139700,
  "created_at": "2025-10-29T12:45:00Z"
}
```

**주문 아이템 조회**:
```bash
curl -X GET "http://localhost:3000/order_items?order_id=5001"
```

**응답**:
```json
[
  {
    "order_item_id": 5001,
    "order_id": 5001,
    "product_id": 1,
    "product_name": "티셔츠",
    "option_id": 101,
    "option_name": "블랙/M",
    "quantity": 2,
    "unit_price": 29900
  },
  {
    "order_item_id": 5002,
    "order_id": 5001,
    "product_id": 2,
    "product_name": "청바지",
    "option_id": 201,
    "option_name": "청색/32",
    "quantity": 1,
    "unit_price": 79900
  }
]
```

---

#### 3.3 주문 목록 조회 (사용자별)

**Endpoint**: `GET /orders`

**요청**:
```bash
# 특정 사용자의 주문 조회
curl -X GET "http://localhost:3000/orders?user_id=100"

# 주문 상태로 필터링
curl -X GET "http://localhost:3000/orders?order_status=COMPLETED"

# 페이지네이션
curl -X GET "http://localhost:3000/orders?user_id=100&_page=1&_limit=10"
```

**응답**:
```json
[
  {
    "order_id": 5001,
    "user_id": 100,
    "order_status": "COMPLETED",
    "subtotal": 139700,
    "coupon_discount": 0,
    "final_amount": 139700,
    "created_at": "2025-10-29T12:45:00Z"
  },
  {
    "order_id": 5002,
    "user_id": 100,
    "order_status": "COMPLETED",
    "subtotal": 99800,
    "coupon_discount": 9980,
    "final_amount": 89820,
    "created_at": "2025-10-28T14:20:00Z"
  }
]
```

---

### 4. 쿠폰 API

#### 4.1 쿠폰 발급 (선착순)

**Endpoint**: `POST /user_coupons`

**요청**:
```bash
curl -X POST "http://localhost:3000/user_coupons" \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": 100,
    "coupon_id": 1,
    "coupon_name": "10% 할인 쿠폰",
    "discount_type": "PERCENTAGE",
    "discount_rate": 0.10,
    "discount_amount": null,
    "status": "ACTIVE",
    "issued_at": "2025-10-29T13:00:00Z",
    "used_at": null,
    "valid_from": "2025-10-29T00:00:00Z",
    "valid_until": "2025-10-31T23:59:59Z"
  }'
```

**응답 (201 Created)**:
```json
{
  "user_coupon_id": 2004,
  "user_id": 100,
  "coupon_id": 1,
  "coupon_name": "10% 할인 쿠폰",
  "discount_type": "PERCENTAGE",
  "discount_rate": 0.10,
  "discount_amount": null,
  "status": "ACTIVE",
  "issued_at": "2025-10-29T13:00:00Z",
  "used_at": null,
  "valid_from": "2025-10-29T00:00:00Z",
  "valid_until": "2025-10-31T23:59:59Z"
}
```

---

#### 4.2 사용자가 보유한 쿠폰 조회

**Endpoint**: `GET /user_coupons`

**요청**:
```bash
# 특정 사용자의 쿠폰 조회
curl -X GET "http://localhost:3000/user_coupons?user_id=100"

# 상태별 필터링
curl -X GET "http://localhost:3000/user_coupons?user_id=100&status=ACTIVE"

# USED 쿠폰만 조회
curl -X GET "http://localhost:3000/user_coupons?user_id=100&status=USED"
```

**응답 (200 OK)**:
```json
[
  {
    "user_coupon_id": 2001,
    "user_id": 100,
    "coupon_id": 1,
    "coupon_name": "10% 할인 쿠폰",
    "discount_type": "PERCENTAGE",
    "discount_rate": 0.10,
    "discount_amount": null,
    "status": "USED",
    "issued_at": "2025-10-29T13:00:00Z",
    "used_at": "2025-10-28T14:20:00Z",
    "valid_from": "2025-10-29T00:00:00Z",
    "valid_until": "2025-10-31T23:59:59Z"
  },
  {
    "user_coupon_id": 2002,
    "user_id": 100,
    "coupon_id": 3,
    "coupon_name": "신규회원 20% 할인",
    "discount_type": "PERCENTAGE",
    "discount_rate": 0.20,
    "discount_amount": null,
    "status": "ACTIVE",
    "issued_at": "2025-10-29T13:10:00Z",
    "used_at": null,
    "valid_from": "2025-10-15T00:00:00Z",
    "valid_until": "2025-11-30T23:59:59Z"
  }
]
```

---

#### 4.3 사용 가능한 쿠폰 조회

**Endpoint**: `GET /coupons`

**요청**:
```bash
# 모든 쿠폰 조회
curl -X GET "http://localhost:3000/coupons"

# 활성화된 쿠폰만 조회 (is_active 필터링 필요)
curl -X GET "http://localhost:3000/coupons?is_active=true"
```

**응답 (200 OK)**:
```json
[
  {
    "coupon_id": 1,
    "coupon_name": "10% 할인 쿠폰",
    "description": "모든 상품 10% 할인",
    "discount_type": "PERCENTAGE",
    "discount_rate": 0.10,
    "discount_amount": null,
    "is_active": true,
    "remaining_qty": 50,
    "valid_from": "2025-10-29T00:00:00Z",
    "valid_until": "2025-10-31T23:59:59Z",
    "version": 3
  },
  {
    "coupon_id": 2,
    "coupon_name": "무료배송",
    "description": "50,000원 이상 주문 무료배송",
    "discount_type": "FIXED_AMOUNT",
    "discount_amount": 5000,
    "discount_rate": null,
    "is_active": true,
    "remaining_qty": 100,
    "valid_from": "2025-10-01T00:00:00Z",
    "valid_until": "2025-12-31T23:59:59Z",
    "version": 2
  }
]
```

---

### 5. 통계 API

#### 5.1 인기 상품 조회 (TOP 5)

**Endpoint**: `GET /products`

**요청**:
```bash
# 상품을 내림차순으로 정렬하여 상위 5개 조회
curl -X GET "http://localhost:3000/products?_sort=total_stock&_order=desc&_limit=5"
```

**참고**: JSON Server에서는 별도의 통계 엔드포인트를 지원하지 않으므로,
프런트엔드나 백엔드에서 별도 로직으로 처리하거나, 미들웨어를 추가해야 합니다.

---

#### 5.2 재고 현황 조회 (상품별)

**Endpoint**: `GET /product_options`

**요청**:
```bash
# 특정 상품의 옵션별 재고 조회
curl -X GET "http://localhost:3000/product_options?product_id=1"
```

**응답 (200 OK)**:
```json
[
  {
    "option_id": 101,
    "product_id": 1,
    "name": "블랙/M",
    "stock": 30,
    "version": 5
  },
  {
    "option_id": 102,
    "product_id": 1,
    "name": "블랙/L",
    "stock": 25,
    "version": 5
  },
  {
    "option_id": 103,
    "product_id": 1,
    "name": "화이트/M",
    "stock": 45,
    "version": 3
  }
]
```

---

## JSON Server 필터링 및 정렬

### 쿼리 파라미터

| 파라미터 | 설명 | 예시 |
|---------|------|------|
| `_page` | 페이지 번호 (기본값: 1) | `?_page=2` |
| `_limit` | 페이지당 항목 수 | `?_limit=10` |
| `_sort` | 정렬 기준 필드 | `?_sort=product_id` |
| `_order` | 정렬 순서 (asc\|desc) | `?_order=desc` |
| 필드명 | 필드 값으로 필터링 | `?status=판매중` |
| `_gte` | 크거나 같음 | `?price_gte=10000` |
| `_lte` | 작거나 같음 | `?price_lte=100000` |
| `_ne` | 같지 않음 | `?status_ne=품절` |
| `_like` | 포함 (regex) | `?product_name_like=셔츠` |

### 예시

```bash
# 가격이 30,000 이상인 상품 조회 (내림차순)
curl -X GET "http://localhost:3000/products?price_gte=30000&_sort=price&_order=desc"

# 현재 판매 중인 상품 중 특정 단어 포함 조회
curl -X GET "http://localhost:3000/products?status=판매중&product_name_like=셔츠"

# 특정 사용자의 ACTIVE 상태 쿠폰 조회
curl -X GET "http://localhost:3000/user_coupons?user_id=100&status=ACTIVE"
```

---

## 데이터베이스 스키마

### users
```json
{
  "user_id": "Long (Primary Key)",
  "username": "String",
  "email": "String",
  "balance": "Long (잔액)",
  "created_at": "Timestamp"
}
```

### products
```json
{
  "product_id": "Long (Primary Key)",
  "product_name": "String",
  "description": "String",
  "price": "Long (원 단위)",
  "total_stock": "Long",
  "status": "String (판매중|품절|단종)",
  "created_at": "Timestamp"
}
```

### product_options
```json
{
  "option_id": "Long (Primary Key)",
  "product_id": "Long (Foreign Key)",
  "name": "String",
  "stock": "Long",
  "version": "Long (낙관적 락)"
}
```

### carts
```json
{
  "cart_id": "Long (Primary Key)",
  "user_id": "Long (Foreign Key)",
  "total_items": "Long",
  "total_price": "Long",
  "updated_at": "Timestamp"
}
```

### cart_items
```json
{
  "cart_item_id": "Long (Primary Key)",
  "cart_id": "Long (Foreign Key)",
  "product_id": "Long",
  "product_name": "String",
  "option_id": "Long",
  "option_name": "String",
  "quantity": "Long",
  "unit_price": "Long",
  "subtotal": "Long"
}
```

### orders
```json
{
  "order_id": "Long (Primary Key)",
  "user_id": "Long (Foreign Key)",
  "order_status": "String (COMPLETED|PENDING|FAILED)",
  "subtotal": "Long",
  "coupon_discount": "Long",
  "coupon_id": "Long (Foreign Key, nullable)",
  "final_amount": "Long",
  "created_at": "Timestamp"
}
```

### order_items
```json
{
  "order_item_id": "Long (Primary Key)",
  "order_id": "Long (Foreign Key)",
  "product_id": "Long",
  "product_name": "String (스냅샷)",
  "option_id": "Long",
  "option_name": "String (스냅샷)",
  "quantity": "Long",
  "unit_price": "Long"
}
```

### coupons
```json
{
  "coupon_id": "Long (Primary Key)",
  "coupon_name": "String",
  "description": "String",
  "discount_type": "String (PERCENTAGE|FIXED_AMOUNT)",
  "discount_rate": "Decimal (PERCENTAGE인 경우)",
  "discount_amount": "Long (FIXED_AMOUNT인 경우)",
  "is_active": "Boolean",
  "remaining_qty": "Long",
  "valid_from": "Timestamp",
  "valid_until": "Timestamp",
  "version": "Long (비관적 락)"
}
```

### user_coupons
```json
{
  "user_coupon_id": "Long (Primary Key)",
  "user_id": "Long (Foreign Key)",
  "coupon_id": "Long (Foreign Key)",
  "coupon_name": "String",
  "discount_type": "String",
  "discount_rate": "Decimal (nullable)",
  "discount_amount": "Long (nullable)",
  "status": "String (ACTIVE|USED|EXPIRED)",
  "issued_at": "Timestamp",
  "used_at": "Timestamp (nullable)",
  "valid_from": "Timestamp",
  "valid_until": "Timestamp"
}
```

---

## 커스텀 라우팅 설정

JSON Server의 기본 라우팅을 `/api` 프리픽스로 사용하려면 라우트 파일을 설정합니다.

### routes.json 파일 생성

```json
{
  "/api/*": "/$1"
}
```

### 서버 실행

```bash
json-server --watch db.json --routes routes.json --port 3000
```

이제 API 요청 시 `/api` 프리픽스를 사용할 수 있습니다:

```bash
curl -X GET "http://localhost:3000/api/products"
```

---

## 미들웨어 (선택사항)

더 복잡한 로직 (예: 트랜잭션, 검증)이 필요한 경우, 커스텀 미들웨어를 작성할 수 있습니다.

### middleware.js 예시

```javascript
module.exports = (req, res, next) => {
  // CORS 설정
  res.header("Access-Control-Allow-Origin", "*");
  res.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
  res.header("Access-Control-Allow-Headers", "Content-Type");

  // 요청 로깅
  if (req.method !== "OPTIONS") {
    console.log(`${req.method} ${req.path}`);
  }

  next();
};
```

### 미들웨어와 함께 서버 실행

```bash
json-server --watch db.json --port 3000 --middlewares ./middleware.js
```

---

## 주의사항

### JSON Server의 제약사항

1. **트랜잭션 미지원**: 여러 리소스를 원자적으로 업데이트할 수 없습니다.
2. **검증 미지원**: 비즈니스 로직 검증이 없습니다 (예: 재고 확인).
3. **동시성 제어**: 낙관적/비관적 락 자동 구현이 없습니다.
4. **복합 쿼리**: 복잡한 필터링이나 집계가 제한적입니다.

### 개발 중 사용 권장사항

- **프로토타이핑**: UI/UX 개발 중 백엔드 없이 빠르게 진행
- **API 테스트**: 백엔드 완성 전 프런트엔드 테스트
- **문서화**: API 명세에 따른 데이터 구조 검증
- **마이그레이션**: 실제 백엔드로 전환 시 db.json 데이터를 기반으로 데이터 초기화

---

## 예제 프로젝트 구조

```
e-commerce/
├── db.json                    # JSON Server 데이터
├── routes.json               # 커스텀 라우팅
├── middleware.js             # 커스텀 미들웨어
├── JSON_SERVER_GUIDE.md      # 이 파일
└── src/
    ├── services/
    │   └── api.js           # API 호출 로직
    └── components/
        └── ...
```

### api.js 예시

```javascript
const API_BASE = 'http://localhost:3000/api';

export const api = {
  // 상품 조회
  getProducts: (page = 0, size = 10) =>
    fetch(`${API_BASE}/products?_page=${page + 1}&_limit=${size}`),

  getProductDetail: (id) =>
    fetch(`${API_BASE}/products/${id}`),

  // 장바구니
  getCart: (cartId) =>
    fetch(`${API_BASE}/carts/${cartId}`),

  addToCart: (item) =>
    fetch(`${API_BASE}/cart_items`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(item)
    }),

  // 주문
  createOrder: (order) =>
    fetch(`${API_BASE}/orders`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(order)
    }),

  // 쿠폰
  getCoupons: () =>
    fetch(`${API_BASE}/coupons`),
};
```

---

## 참고 링크

- [JSON Server 공식 문서](https://github.com/typicode/json-server)
- [API 명세](../docs/api/api-specification.md)
- [데이터 모델](../docs/api/data-models.md)