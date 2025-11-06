# E-Commerce REST API 명세서

## 목차

1. [개요](#개요)
2. [상품 조회 API](#상품-조회-api)
3. [장바구니 API](#장바구니-api)
4. [주문 API](#주문-api)
5. [쿠폰 API](#쿠폰-api)
6. [통계 API](#통계-api)
7. [에러 응답](#에러-응답)

---

## 개요

**Base URL**: `/api`
**응답 형식**: JSON
**주요 설계 원칙**:
- 옵션 기반 재고 관리 (요구사항 2.1.4)
- 원자적 주문 처리 (요구사항 2.2.2)
- 선착순 쿠폰 발급 (요구사항 2.3.1)
- 비동기 외부 전송 (요구사항 2.4)

---

## 상품 조회 API

### 1.1 상품 목록 조회

**Endpoint**: `GET /products`

**Method**: GET

**Query Parameters**:
```
- page (Integer, optional, default: 0): 페이지 번호 (≥ 0)
- size (Integer, optional, default: 10): 페이지당 항목 수 (1 ≤ size ≤ 100)
- sort (String, optional, default: "product_id,desc"): 정렬 기준
  (product_id | product_name | price | created_at, asc|desc)
```

**Request Examples**:
```
GET /api/products?page=0&size=10&sort=product_id,desc
GET /api/products?page=1&size=20&sort=price,asc
GET /api/products (기본값: page=0, size=10, sort=product_id,desc)
```

**Success Response (200 OK)**:
```json
{
  "content": [
    {
      "product_id": 1,
      "product_name": "티셔츠",
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
  ],
  "totalElements": 50,
  "totalPages": 5,
  "currentPage": 0,
  "size": 10
}
```

**Response Fields**:
- `content` (Array): 상품 목록
  - `product_id` (Long): 상품 고유 식별자 (PK)
  - `product_name` (String): 상품명 (1~255자)
  - `description` (String): 상품 설명 (0~1000자)
  - `price` (Long): 상품 가격 (원 단위, ≥ 0)
  - `total_stock` (Long): 총 재고 수량 = SUM(product_options.stock) (≥ 0)
  - `status` (String): 판매 상태 (판매 중 | 품절 | 판매 중지)
  - `created_at` (Timestamp): 상품 생성 시각 (ISO 8601)
- `totalElements` (Long): 전체 상품 수
- `totalPages` (Long): 전체 페이지 수
- `currentPage` (Integer): 현재 페이지 번호 (0-based)
- `size` (Integer): 페이지당 항목 수

**Status Codes**:
- `200 OK`: 상품 목록 조회 성공 (빈 목록도 200 반환)
- `400 Bad Request`: 유효하지 않은 페이지 파라미터

**Error Response (400)**:
```json
{
  "error_code": "INVALID_REQUEST",
  "error_message": "페이지 크기는 1 이상 100 이하여야 합니다",
  "timestamp": "2025-10-29T12:45:00Z",
  "request_id": "req-abc123def456"
}
```

**Common Errors**:
- `page=-1`: 음수 페이지 번호
- `size=0`: 페이지 크기가 0
- `size=101`: 페이지 크기 초과 (최대 100)
- `sort=invalid_field`: 존재하지 않는 정렬 필드

**Entity Relations** (data-models.md):
- products: product_id(PK), product_name, description, price, total_stock, status, created_at

**Sequence Diagram** (sequence-diagrams.md): N/A (직접 조회)

**Performance** (요구사항 3.1):
- 응답 시간: 평균 < 1초
- 인덱싱: created_at DESC, product_id DESC

---

### 1.2 상품 상세 조회 (옵션 포함)

**Endpoint**: `GET /products/{product_id}`

**Method**: GET

**Path Parameters**:
```
- product_id (Long, required): 상품 고유 식별자 (> 0)
```

**Success Response (200 OK)**:
```json
{
  "product_id": 1,
  "product_name": "티셔츠",
  "description": "100% 면 티셔츠",
  "price": 29900,
  "total_stock": 100,
  "status": "판매 중",
  "options": [
    {
      "option_id": 101,
      "name": "블랙/M",
      "stock": 30,
      "version": 5
    },
    {
      "option_id": 102,
      "name": "블랙/L",
      "stock": 25,
      "version": 5
    },
    {
      "option_id": 103,
      "name": "화이트/M",
      "stock": 45,
      "version": 3
    }
  ],
  "created_at": "2025-10-29T10:00:00Z"
}
```

**Response Fields**:
- `product_id` (Long): 상품 고유 식별자 (PK)
- `product_name` (String): 상품명 (1~255자)
- `description` (String): 상품 설명 (0~1000자)
- `price` (Long): 상품 단가 (원 단위, ≥ 0)
- `total_stock` (Long): 총 재고 수량 = SUM(product_options.stock) (≥ 0)
- `status` (String): 판매 상태 (판매 중 | 품절 | 판매 중지)
- `options` (Array): 상품 옵션 목록
  - `option_id` (Long): 옵션 고유 식별자 (PK)
  - `name` (String): 옵션 이름 (예: "검정/M", "파랑/32") (1~100자)
  - `stock` (Integer): 현재 옵션별 재고 (≥ 0)
  - `version` (Long): 낙관적 락 버전 (동시성 제어용, ≥ 0)
- `created_at` (Timestamp): 상품 생성 시각 (ISO 8601)

**Status Codes**:
- `200 OK`: 상품 조회 성공
- `400 Bad Request`: 유효하지 않은 product_id (음수 또는 0)
- `404 Not Found`: 상품을 찾을 수 없음 (존재하지 않는 product_id)

**Error Response (404)**:
```json
{
  "error_code": "PRODUCT_NOT_FOUND",
  "error_message": "상품을 찾을 수 없습니다 (ID: 999)",
  "timestamp": "2025-10-29T12:45:00Z",
  "request_id": "req-abc123def456"
}
```

**Error Response (400)**:
```json
{
  "error_code": "INVALID_REQUEST",
  "error_message": "product_id는 양수여야 합니다",
  "timestamp": "2025-10-29T12:45:00Z",
  "request_id": "req-abc123def456"
}
```

**Entity Relations** (data-models.md):
- products: product_id(PK), product_name, description, price, total_stock, status, created_at
- product_options: option_id(PK), product_id(FK), name, stock, version

**Sequence Diagram** (sequence-diagrams.md): "1. 상품 조회 흐름"

**Business Rules** (BR-02, BR-04-2):
- total_stock = SUM(product_options.stock) (계산된 필드)
- 옵션별 재고는 음수가 될 수 없음 (stock ≥ 0)
- 모든 옵션의 stock이 0이면 status는 "품절"로 변경

**Performance** (요구사항 3.1):
- 응답 시간: < 1초
- 인덱싱: product_id (PK), product_options.product_id (FK)

---

### 1.3 인기 상품 조회 (TOP 5, 최근 3일)

**Endpoint**: `GET /products/popular`

**Method**: GET

**Query Parameters**: 없음

**Success Response (200 OK)**:
```json
{
  "products": [
    {
      "product_id": 1,
      "product_name": "티셔츠",
      "price": 29900,
      "total_stock": 100,
      "status": "판매 중",
      "order_count_3days": 150,
      "rank": 1
    },
    {
      "product_id": 5,
      "product_name": "슬리퍼",
      "price": 19900,
      "total_stock": 200,
      "status": "판매 중",
      "order_count_3days": 120,
      "rank": 2
    }
  ]
}
```

**Response Fields**:
- `products` (Array): 인기 상품 목록 (최대 5개)
  - `product_id` (Long): 상품 고유 식별자
  - `product_name` (String): 상품명
  - `price` (Long): 상품 가격 (원 단위)
  - `total_stock` (Long): 현재 재고 수량
  - `status` (String): 판매 상태 (판매 중 | 품절 | 판매 중지)
  - `order_count_3days` (Long): 최근 3일간 판매 수량 (순위 기준)
  - `rank` (Integer): 순위 (1~5)

**Status Codes**:
- `200 OK`: 인기 상품 조회 성공 (빈 결과도 200 반환)

**Business Rules** (요구사항 2.1.3):
- **집계 기간**: 현재 기준 최근 3일 (NOW() - 3days)
- **순위 기준**: order_count_3days (내림차순, 판매 수량 많은 순)
- **결과 제한**: 상위 5개만 반환
- **재고 포함**: 품절 상품도 포함하여 조회

**Performance** (요구사항 3.1):
- 응답 시간: < 2초
- 캐싱: 1시간 TTL (실시간 집계 부담 경감)

---

## 장바구니 API

### 2.1 장바구니 조회

**Endpoint**: `GET /carts`

**Method**: GET

**Headers**:
```
X-USER-ID: Long (required) - 사용자 고유 식별자
```

**Request Body**: 없음

**Success Response (200 OK)**:
```json
{
  "cart_id": 1,
  "user_id": 100,
  "total_items": 3,
  "total_price": 109700,
  "items": [
    {
      "cart_item_id": 1001,
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
      "product_id": 5,
      "product_name": "슬리퍼",
      "option_id": 501,
      "option_name": "검정/260mm",
      "quantity": 1,
      "unit_price": 19900,
      "subtotal": 19900
    }
  ],
  "updated_at": "2025-10-29T12:30:00Z"
}
```

**Response Fields**:
- `cart_id` (Long): 장바구니 고유 식별자 (사용자별 1:1, PK)
- `user_id` (Long): 사용자 고유 식별자 (FK)
- `total_items` (Integer): 장바구니 내 총 아이템 수 (≥ 0)
- `total_price` (Long): 장바구니 총 금액 = SUM(cart_items.subtotal) (원 단위, ≥ 0)
- `items` (Array): 장바구니 아이템 목록 (최대 1000개)
  - `cart_item_id` (Long): 장바구니 아이템 고유 식별자 (PK)
  - `product_id` (Long): 상품 고유 식별자 (FK)
  - `product_name` (String): 상품명 (스냅샷, 1~255자)
  - `option_id` (Long): 옵션 고유 식별자 (FK, NOT NULL)
  - `option_name` (String): 옵션명 (스냅샷, 예: "검정/M", "파랑/32")
  - `quantity` (Integer): 수량 (1 ≤ qty ≤ 1000)
  - `unit_price` (Long): 단가 (추가 시점의 상품 가격, 원 단위)
  - `subtotal` (Long): 소계 = unit_price * quantity (원 단위, 계산 필드)
- `updated_at` (Timestamp): 장바구니 마지막 수정 시각 (ISO 8601)

**Status Codes**:
- `200 OK`: 장바구니 조회 성공 (빈 장바구니도 200)

**Entity Relations** (data-models.md):
- carts: cart_id(PK), user_id(FK, UNIQUE), total_items, total_price, updated_at
- cart_items: cart_item_id(PK), cart_id(FK), product_id(FK), option_id(FK, NOT NULL), quantity, unit_price, subtotal

**Sequence Diagram** (sequence-diagrams.md): "2. 장바구니 담기 흐름"

**Business Rules** (요구사항 2.2.1):
- 사용자당 1개의 장바구니만 존재 (1:1 관계)
- 재고 차감 없음 (주문 시점에만 차감)
- option_id는 필수 (NOT NULL)
- subtotal은 조회 시 실시간 계산

---

### 2.2 장바구니 아이템 추가

**Endpoint**: `POST /carts/items`

**Method**: POST

**Headers**:
```
X-USER-ID: Long (required) - 사용자 고유 식별자
```

**Request Body**:
```json
{
  "product_id": 1,
  "option_id": 101,
  "quantity": 2
}
```

**Request Fields**:
- `product_id` (Long, required): 상품 고유 식별자 (> 0)
- `option_id` (Long, required): 옵션 고유 식별자 (> 0, NOT NULL)
- `quantity` (Integer, required): 수량 (1 ≤ qty ≤ 1000)

**Success Response (201 Created)**:
```json
{
  "cart_item_id": 1001,
  "cart_id": 1,
  "product_id": 1,
  "product_name": "티셔츠",
  "option_id": 101,
  "option_name": "블랙/M",
  "quantity": 2,
  "unit_price": 29900,
  "subtotal": 59800,
  "created_at": "2025-10-29T12:30:00Z"
}
```

**Response Fields**:
- `cart_item_id` (Long): 장바구니 아이템 고유 식별자 (PK)
- `cart_id` (Long): 장바구니 ID (사용자별 1:1)
- `product_id` (Long): 상품 고유 식별자
- `product_name` (String): 상품명 (스냅샷)
- `option_id` (Long): 옵션 고유 식별자
- `option_name` (String): 옵션명 (스냅샷)
- `quantity` (Integer): 수량
- `unit_price` (Long): 추가 시점의 상품 단가 (원 단위)
- `subtotal` (Long): 소계 = unit_price * quantity (원 단위)
- `created_at` (Timestamp): 아이템 추가 시각 (ISO 8601)

**Status Codes**:
- `201 Created`: 장바구니 아이템 추가 성공
- `400 Bad Request`: 유효하지 않은 요청 파라미터
- `404 Not Found`: 상품 또는 옵션을 찾을 수 없음

**Error Response (400)**:
```json
{
  "error_code": "INVALID_REQUEST",
  "error_message": "수량은 1 이상 1000 이하여야 합니다",
  "timestamp": "2025-10-29T12:45:00Z",
  "request_id": "req-abc123def456"
}
```

**Error Response (404)**:
```json
{
  "error_code": "PRODUCT_NOT_FOUND",
  "error_message": "상품을 찾을 수 없습니다 (ID: 999)",
  "timestamp": "2025-10-29T12:45:00Z",
  "request_id": "req-abc123def456"
}
```

**Common Errors**:
- `quantity=0`: 수량이 0 (최소 1 필요)
- `quantity=1001`: 수량 초과 (최대 1000)
- `product_id=null`: product_id 누락
- `option_id` 존재하지 않음: 유효하지 않은 옵션

**Entity Relations** (data-models.md):
- carts: cart_id(PK), user_id(FK, UNIQUE)
- cart_items: cart_item_id(PK), cart_id(FK), product_id(FK), option_id(FK, NOT NULL), quantity, unit_price, subtotal
- products: product_id(PK), product_name, price
- product_options: option_id(PK), product_id(FK), name, stock

**Business Rules** (요구사항 2.2.1, BR-03):
- 장바구니 추가 단계에서는 재고 차감 없음
- option_id는 필수 (NOT NULL)
- 가격은 추가 시점의 상품 가격으로 스냅샷 저장
- 동일한 상품-옵션 조합을 다시 추가하면 기존 아이템의 수량 증가 처리 가능

**Sequence Diagram** (sequence-diagrams.md): "2. 장바구니 담기 흐름"

**Performance** (요구사항 3.1):
- 응답 시간: < 1초

---

### 2.3 장바구니 아이템 수량 수정

**Endpoint**: `PUT /carts/items/{cart_item_id}`

**Method**: PUT

**Headers**:
```
X-USER-ID: Long (required) - 사용자 고유 식별자
```

**Path Parameters**:
```
- cart_item_id (Long, required): 장바구니 아이템 ID
```

**Request Body**:
```json
{
  "quantity": 5
}
```

**Request Schema**:
```
{
  "quantity": Integer (required, 0 < qty ≤ 1000)
}
```

**Response (200 OK)**:
```json
{
  "cart_item_id": 1001,
  "cart_id": 1,
  "product_id": 1,
  "option_id": 101,
  "quantity": 5,
  "unit_price": 29900,
  "subtotal": 149500,
  "updated_at": "2025-10-29T12:35:00Z"
}
```

**Status Codes**:
- `200 OK`: 수정 성공
- `400 Bad Request`: 유효하지 않은 수량
- `404 Not Found`: 장바구니 아이템을 찾을 수 없음

---

### 2.4 장바구니 아이템 제거

**Endpoint**: `DELETE /carts/items/{cart_item_id}`

**Method**: DELETE

**Headers**:
```
X-USER-ID: Long (required) - 사용자 고유 식별자
```

**Path Parameters**:
```
- cart_item_id (Long, required): 장바구니 아이템 ID
```

**Request Body**: 없음

**Response (204 No Content)**:
```
(응답 본문 없음)
```

**Status Codes**:
- `204 No Content`: 삭제 성공
- `404 Not Found`: 장바구니 아이템을 찾을 수 없음

---

## 주문 API

### 3.1 주문 생성 (3단계 프로세스)

**Endpoint**: `POST /orders`

**Method**: POST

**Headers**:
```
X-USER-ID: Long (required) - 사용자 고유 식별자
```

**Request Body**:
```json
{
  "order_items": [
    {
      "product_id": 1,
      "option_id": 101,
      "quantity": 2
    },
    {
      "product_id": 2,
      "option_id": 201,
      "quantity": 1
    }
  ],
  "coupon_id": null
}
```

**Request Schema**:
```
{
  "order_items": Array (required, min: 1 item) [
    {
      "product_id": Long (required, > 0),
      "option_id": Long (required, > 0),
      "quantity": Integer (required, > 0)
    }
  ],
  "coupon_id": Long (optional, nullable)
}
```

**Response (201 Created)**:
```json
{
  "order_id": 5001,
  "user_id": 100,
  "order_status": "COMPLETED",
  "subtotal": 139700,
  "coupon_discount": 0,
  "coupon_id": null,
  "final_amount": 139700,
  "order_items": [
    {
      "order_item_id": 5001,
      "product_id": 1,
      "product_name": "티셔츠",
      "option_id": 101,
      "option_name": "블랙/M",
      "quantity": 2,
      "unit_price": 29900
    },
    {
      "order_item_id": 5002,
      "product_id": 2,
      "product_name": "청바지",
      "option_id": 201,
      "option_name": "청색/32",
      "quantity": 1,
      "unit_price": 79900
    }
  ],
  "created_at": "2025-10-29T12:45:00Z"
}
```

**Status Codes**:
- `201 Created`: 주문 생성 성공
- `400 Bad Request`: INVALID_PRODUCT_OPTION - 상품과 옵션 불일치
- `400 Bad Request`: ERR-001 - "[옵션명]의 재고가 부족합니다"
- `400 Bad Request`: ERR-002 - "잔액이 부족합니다"
- `400 Bad Request`: ERR-003 - "유효하지 않은 쿠폰입니다"
- `404 Not Found`: PRODUCT_NOT_FOUND - 상품을 찾을 수 없음
- `404 Not Found`: OPTION_NOT_FOUND - 옵션을 찾을 수 없음
- `500 Internal Server Error`: ERR-004 - "주문 생성에 실패했습니다" (거래 실패)

**주문 생성 프로세스** (sequence-diagrams-ko.md "3. 주문 생성 흐름 (3단계)" 참고):

**1단계: 검증 (읽기 전용)**
- 각 상품의 옵션별 재고 확인: `option_stock >= quantity` (요구사항 2.1.4)
- 쿠폰 유효성 검증: 유효기간, 활성 상태, 발급 수량 (요구사항 2.3.1, 2.3.2)
- 최종 결제금액 계산: `final_amount = subtotal - coupon_discount` (요구사항 2.2.4)
- 사용자 잔액 확인: `balance >= final_amount` (요구사항 2.2.3)

**2단계: 원자적 거래 (모두 성공하거나 모두 롤백)**
```
BEGIN TRANSACTION
  - UPDATE product_options SET stock = stock - qty WHERE option_id = ? AND version = current_version
    (낙관적 락: version 불일치 시 ERR-004 반환 → Race Condition 방지)
  - UPDATE users SET balance = balance - final_amount WHERE user_id = ?
  - INSERT orders, order_items (product_name, option_name 스냅샷 저장)
  - UPDATE user_coupons SET status = 'USED', used_at = NOW() (쿠폰 사용 처리)
  - UPDATE products SET status = '품절' (if all options stock=0)
COMMIT (또는 ROLLBACK on version mismatch)
```

**3단계: 비동기 외부 전송**
- INSERT outbox (order_id, message_type='SHIPPING_REQUEST', status='PENDING') (요구사항 2.4.1)
- 별도 배치 프로세스가 비동기로 처리 (요구사항 2.4.2)
- 주문 상태는 즉시 COMPLETED 반환

**Entity Relations** (data-model.md):
- orders: order_id, user_id, order_status, coupon_id, coupon_discount, subtotal, final_amount
- order_items: order_item_id, order_id, product_id, option_id, product_name, option_name, quantity, unit_price
- product_options: version (낙관적 락)

**Business Rules** (BR-02, BR-04, BR-04-1, BR-05, BR-06, BR-14, BR-15, BR-16):
- 옵션별 재고 검증 (상품 총 재고 X)
- 옵션 ID는 필수
- 모든 상품의 재고가 충분해야 함 (부분 주문 불가)
- 결제금액 = 상품 합계 - 쿠폰 할인액
- 주문과 외부 전송 분리

**Concurrency Control** (요구사항 3.2):
- 낙관적 락: product_options.version으로 Race Condition 방지

**Performance** (요구사항 3.1):
- 응답 시간: < 3초

**Sequence Diagram**: sequence-diagrams-ko.md "3. 주문 생성 흐름 (3단계)"

---

### 3.2 주문 상세 조회

**Endpoint**: `GET /orders/{order_id}`

**Method**: GET

**Headers**:
```
X-USER-ID: Long (required) - 사용자 고유 식별자
```

**Path Parameters**:
```
- order_id (Long, required): 주문 ID
```

**Response (200 OK)**:
```json
{
  "order_id": 5001,
  "user_id": 100,
  "order_status": "COMPLETED",
  "subtotal": 139700,
  "coupon_discount": 0,
  "coupon_id": null,
  "final_amount": 139700,
  "order_items": [
    {
      "order_item_id": 5001,
      "product_id": 1,
      "product_name": "티셔츠",
      "option_id": 101,
      "option_name": "블랙/M",
      "quantity": 2,
      "unit_price": 29900
    }
  ],
  "created_at": "2025-10-29T12:45:00Z"
}
```

**Status Codes**:
- `200 OK`: 요청 성공
- `404 Not Found`: 주문을 찾을 수 없음

**Entity Relations** (data-model.md):
- orders, order_items (스냅샷 포함)

---

### 3.3 주문 목록 조회 (사용자별)

**Endpoint**: `GET /orders`

**Method**: GET

**Headers**:
```
X-USER-ID: Long (required) - 사용자 고유 식별자
```

**Query Parameters**:
```
- page (Integer, optional, default: 0): 페이지 번호
- size (Integer, optional, default: 10): 페이지당 항목 수
- status (String, optional): COMPLETED | PENDING | FAILED
```

**Response (200 OK)**:
```json
{
  "content": [
    {
      "order_id": 5001,
      "order_status": "COMPLETED",
      "subtotal": 139700,
      "coupon_discount": 0,
      "final_amount": 139700,
      "created_at": "2025-10-29T12:45:00Z"
    }
  ],
  "totalElements": 15,
  "totalPages": 2,
  "currentPage": 0,
  "size": 10
}
```

**Status Codes**:
- `200 OK`: 요청 성공

---

## 쿠폰 API

### 4.1 쿠폰 발급 (선착순)

**Endpoint**: `POST /coupons/issue`

**Method**: POST

**Request Headers** (required):
```
X-USER-ID: Long (required) - 사용자 식별자
Content-Type: application/json
```

**Request Body**:
```json
{
  "coupon_id": 1
}
```

**Request Schema**:
```
Headers:
{
  "X-USER-ID": Long (required)
}

Body:
{
  "coupon_id": Long (required)
}
```

**Response (201 Created)**:
```json
{
  "user_coupon_id": 2001,
  "user_id": 100,
  "coupon_id": 1,
  "coupon_name": "10% 할인 쿠폰",
  "discount_type": "PERCENTAGE",
  "discount_amount": null,
  "discount_rate": 0.10,
  "status": "ACTIVE",
  "issued_at": "2025-10-29T13:00:00Z",
  "valid_from": "2025-10-29T00:00:00Z",
  "valid_until": "2025-10-31T23:59:59Z"
}
```

**Response Fields**:
- `user_coupon_id` (Long): 사용자 쿠폰 발급 ID
- `user_id` (Long): 사용자 ID (X-USER-ID 헤더에서 추출)
- `coupon_id` (Long): 쿠폰 ID
- `coupon_name` (String): 쿠폰 이름
- `discount_type` (String): 할인 타입 (FIXED_AMOUNT | PERCENTAGE)
- `discount_amount` (Long, nullable): 고정 할인액
- `discount_rate` (Double): 할인율
- `status` (String): 쿠폰 상태 (ACTIVE | USED | EXPIRED)
- `issued_at` (Timestamp): 발급 시각
- `valid_from` (Timestamp): 유효 시작 시각
- `valid_until` (Timestamp): 유효 종료 시각

**Status Codes**:
- `201 Created`: 쿠폰 발급 성공
- `404 Not Found`: COUPON_NOT_FOUND - 쿠폰을 찾을 수 없음
- `400 Bad Request`: ERR-003 - "쿠폰이 모두 소진되었습니다" (재고 부족)
- `400 Bad Request`: ERR-003 - "쿠폰이 유효기간을 벗어났습니다" (유효 기간 검증 실패)
- `400 Bad Request`: ERR-003 - "이 쿠폰은 이미 발급받으셨습니다" (중복 발급 방지)
- `400 Bad Request`: ERR-003 - "쿠폰이 비활성화되어 있습니다" (is_active=false)

**아키텍처** (4계층):
- **Presentation**: `CouponController.issueCoupon(userId, couponId)` - X-USER-ID 헤더에서 userId 추출
- **Application**: `CouponService.issueCoupon(userId, couponId)` - 비즈니스 로직 및 검증
- **Domain**: `Coupon`, `UserCoupon` 엔티티 + 예외 처리
- **Infrastructure**: `CouponRepository`, `UserCouponRepository` - ConcurrentHashMap 기반

**발급 프로세스** (sequence-diagrams.md "5. 쿠폰 발급 흐름" 참고):

```
1. Presentation: X-USER-ID 헤더에서 userId 추출
   ↓
2. Application (CouponService):
   - 비관적 락: synchronized 블록으로 동시성 제어
   - 검증:
     * is_active=true (요구사항 2.3.1)
     * valid_from <= NOW <= valid_until (요구사항 2.3.2)
     * remaining_qty > 0 (요구사항 2.3.1)
   - UNIQUE 확인: UNIQUE(user_id, coupon_id)로 중복 발급 방지
   - 원자적 감소: remaining_qty--, version++
   ↓
3. Infrastructure:
   - CouponRepository.save(coupon) - remaining_qty 업데이트
   - UserCouponRepository.save(userCoupon) - 발급 기록 저장
   ↓
4. Response: user_coupon_id, user_id, coupon_id, ...
```

**Entity Relations** (data-models.md):
- **coupons**: coupon_id, coupon_name, discount_type, discount_amount, discount_rate, remaining_qty, version, is_active, valid_from, valid_until
- **user_coupons**: user_coupon_id, user_id, coupon_id, status (ACTIVE/USED/EXPIRED), issued_at

**Business Rules** (요구사항 2.3):
- 쿠폰은 발급 가능 수량 범위 내에서만 발급 (요구사항 2.3.1)
- 선착순 발급 시 수량 초과 방지 (요구사항 2.3.1)
- 유효 기간 검증 (요구사항 2.3.2)
- 중복 발급 방지 (UNIQUE constraint)

**Concurrency Control** (InMemory DB):
- **동시성 제어**: ConcurrentHashMap + synchronized 블록으로 race condition 방지
- **비관적 락 시뮬레이션**: synchronized로 같은 쿠폰의 동시 발급 요청 차단
- **버전 관리**: version 필드로 낙관적 락 지원 (향후 DB 마이그레이션 대비)

---

### 4.2 사용자가 보유한 쿠폰 조회

**Endpoint**: `GET /coupons/issued`

**Method**: GET

**Request Headers** (required):
```
X-USER-ID: Long (required) - 사용자 식별자
```

**Query Parameters**:
```
- status (String, optional, default: "ACTIVE"): ACTIVE | USED | EXPIRED
```

**Response (200 OK)**:
```json
{
  "user_coupons": [
    {
      "user_coupon_id": 2001,
      "coupon_id": 1,
      "coupon_name": "10% 할인 쿠폰",
      "discount_type": "PERCENTAGE",
      "discount_rate": 0.10,
      "status": "ACTIVE",
      "issued_at": "2025-10-29T13:00:00Z",
      "used_at": null,
      "valid_from": "2025-10-29T00:00:00Z",
      "valid_until": "2025-10-31T23:59:59Z"
    }
  ]
}
```

**Response Fields**:
- `user_coupons` (Array): 사용자가 보유한 쿠폰 목록
  - `user_coupon_id` (Long): 발급 기록 ID
  - `coupon_id` (Long): 쿠폰 ID
  - `coupon_name` (String): 쿠폰 이름
  - `discount_type` (String): 할인 타입 (FIXED_AMOUNT | PERCENTAGE)
  - `discount_rate` (Double): 할인율
  - `status` (String): 쿠폰 상태 (ACTIVE | USED | EXPIRED)
  - `issued_at` (Timestamp): 발급 시각
  - `used_at` (Timestamp, nullable): 사용 시각 (status=USED일 때만 설정)
  - `valid_from` (Timestamp): 유효 시작 시각
  - `valid_until` (Timestamp): 유효 종료 시각

**Status Codes**:
- `200 OK`: 요청 성공 (빈 배열도 200 반환)

**아키텍처** (4계층):
- **Presentation**: `CouponController.getUserCoupons(userId, status)` - X-USER-ID 헤더에서 userId 추출
- **Application**: `CouponService.getUserCoupons(userId, status)` - status별 필터링
- **Domain**: `UserCoupon` 엔티티
- **Infrastructure**: `UserCouponRepository.findByUserId(userId, status)` - ConcurrentHashMap 기반 조회

**Business Logic**:
- 사용자의 쿠폰 발급 기록을 상태별로 조회
- status 파라미터로 필터링 (기본값: ACTIVE)
- 현재 사용자(X-USER-ID)의 쿠폰만 조회 가능

---

### 4.3 사용 가능한 쿠폰 조회

**Endpoint**: `GET /coupons`

**Method**: GET

**Request Headers** (optional):
```
X-USER-ID: Long (optional) - 사용자 식별자 (조회만 필요한 경우 생략 가능)
```

**Query Parameters**: 없음

**Response (200 OK)**:
```json
{
  "coupons": [
    {
      "coupon_id": 1,
      "coupon_name": "10% 할인 쿠폰",
      "description": "모든 상품 10% 할인",
      "discount_type": "PERCENTAGE",
      "discount_rate": 0.10,
      "valid_from": "2025-10-29T00:00:00Z",
      "valid_until": "2025-10-31T23:59:59Z",
      "remaining_qty": 50
    },
    {
      "coupon_id": 2,
      "coupon_name": "무료배송",
      "description": "50,000원 이상 주문 무료배송",
      "discount_type": "FIXED_AMOUNT",
      "discount_amount": 5000,
      "valid_from": "2025-10-01T00:00:00Z",
      "valid_until": "2025-12-31T23:59:59Z",
      "remaining_qty": 100
    }
  ]
}
```

**Response Fields**:
- `coupons` (Array): 발급 가능한 쿠폰 목록 (active=true, 유효 기간 내, remaining_qty > 0)
  - `coupon_id` (Long): 쿠폰 ID
  - `coupon_name` (String): 쿠폰 이름
  - `description` (String): 쿠폰 설명
  - `discount_type` (String): 할인 타입 (FIXED_AMOUNT | PERCENTAGE)
  - `discount_rate` (Double): 할인율 (discount_type=PERCENTAGE일 때)
  - `discount_amount` (Long): 할인액 (discount_type=FIXED_AMOUNT일 때)
  - `valid_from` (Timestamp): 유효 시작 시각
  - `valid_until` (Timestamp): 유효 종료 시각
  - `remaining_qty` (Integer): 남은 발급 가능 수량

**Status Codes**:
- `200 OK`: 요청 성공 (빈 배열도 200 반환)

**아키텍처** (4계층):
- **Presentation**: `CouponController.getAvailableCoupons()` - 사용자 정보 필요 없음 (공개 조회)
- **Application**: `CouponService.getAvailableCoupons()` - 발급 가능 쿠폰만 필터링
- **Domain**: `Coupon` 엔티티
- **Infrastructure**: `CouponRepository.findAllAvailable()` - ConcurrentHashMap 기반 조회

**Business Logic**:
- 현재 발급 가능한 모든 쿠폰 조회 (필터링 조건)
  * is_active = true
  * valid_from <= NOW <= valid_until
  * remaining_qty > 0
- 사용자 로그인 없이 접근 가능 (공개 조회)
- 사용자가 이미 발급받은 쿠폰도 표시됨 (선택지 제공)

---

## 통계 API

### 5.1 인기 상품 조회 (TOP 5, 최근 3일)

**Endpoint**: `GET /products/popular`

**Method**: GET

**Query Parameters**: 없음

**Response (200 OK)**:
```json
{
  "products": [
    {
      "product_id": 1,
      "product_name": "티셔츠",
      "price": 29900,
      "total_stock": 100,
      "status": "판매 중",
      "order_count_3days": 150,
      "rank": 1
    },
    {
      "product_id": 5,
      "product_name": "슬리퍼",
      "price": 19900,
      "total_stock": 200,
      "status": "판매 중",
      "order_count_3days": 120,
      "rank": 2
    }
  ]
}
```

**Status Codes**:
- `200 OK`: 요청 성공

**Performance** (요구사항 3.1):
- 응답 시간: < 2초

**Business Rules** (요구사항 2.1.3):
- 최근 3일간 인기 상품 기준
- 상위 5개만 반환

---

### 5.2 재고 현황 조회 (상품별)

**Endpoint**: `GET /inventory/{product_id}`

**Method**: GET

**Path Parameters**:
```
- product_id (Long, required): 상품 ID
```

**Response (200 OK)**:
```json
{
  "product_id": 1,
  "product_name": "티셔츠",
  "total_stock": 100,
  "options": [
    {
      "option_id": 101,
      "name": "블랙/M",
      "stock": 30,
      "version": 5
    },
    {
      "option_id": 102,
      "name": "블랙/L",
      "stock": 25,
      "version": 5
    },
    {
      "option_id": 103,
      "name": "화이트/M",
      "stock": 45,
      "version": 3
    }
  ]
}
```

**Status Codes**:
- `200 OK`: 요청 성공
- `404 Not Found`: 상품을 찾을 수 없음

**Entity Relations** (data-model.md):
- products: product_id, product_name, total_stock
- product_options: option_id, name, stock, version

**Business Rules** (BR-04-2):
- total_stock = SUM(product_options.stock)

---

## 에러 응답

### 에러 응답 형식

```json
{
  "error_code": "ERR-001",
  "error_message": "[옵션명]의 재고가 부족합니다",
  "timestamp": "2025-10-29T12:45:00Z",
  "request_id": "req-abc123def456"
}
```

### 에러 코드 정의

| Error Code | HTTP Status | Message | Description |
|-----------|-----------|---------|-------------|
| ERR-001 | 400 | "[옵션명]의 재고가 부족합니다" | 옵션 재고 부족 (요구사항 2.1.4) |
| ERR-002 | 400 | "잔액이 부족합니다" | 사용자 충전 잔액 부족 (요구사항 2.2.3) |
| ERR-003 | 400 | "유효하지 않은 쿠폰입니다" | 쿠폰 검증 실패 (요구사항 2.3.1, 2.3.2) |
| ERR-004 | 500 | "주문 생성에 실패했습니다" | 거래 실패 - 버전 불일치 (요구사항 3.2) |
| INVALID_REQUEST | 400 | "요청이 유효하지 않습니다" | 파라미터 검증 실패 |
| INVALID_PRODUCT_OPTION | 400 | "상품과 옵션이 일치하지 않습니다" | product_id와 option_id 불일치 |
| PRODUCT_NOT_FOUND | 404 | "상품을 찾을 수 없습니다" | 존재하지 않는 상품 ID |
| OPTION_NOT_FOUND | 404 | "옵션을 찾을 수 없습니다" | 존재하지 않는 옵션 ID |
| COUPON_NOT_FOUND | 404 | "쿠폰을 찾을 수 없습니다" | 존재하지 않는 쿠폰 ID |
| NOT_FOUND | 404 | "요청한 리소스를 찾을 수 없습니다" | 일반적인 리소스 미존재 |
| CONFLICT | 409 | "충돌이 발생했습니다" | 동시성 제어 실패 (버전 불일치) |
| INTERNAL_SERVER_ERROR | 500 | "서버 오류가 발생했습니다" | 서버 내부 오류 |

---

## 부록 A: HTTP 상태 코드 매핑

| Status Code | Usage |
|-----------|-------|
| 200 OK | 조회 성공, 응답 본문 있음 |
| 201 Created | 리소스 생성 성공 |
| 204 No Content | 요청 성공, 응답 본문 없음 |
| 400 Bad Request | 파라미터 검증 실패 또는 비즈니스 로직 실패 |
| 404 Not Found | 리소스를 찾을 수 없음 |
| 409 Conflict | 동시성 제어 실패 (낙관적 락 버전 불일치) |
| 500 Internal Server Error | 서버 오류 (거래 실패 등) |

---

## 부록 B: 주요 비즈니스 로직

### 주문 생성 프로세스 (3단계)

**Stage 1: Validation (Read-only)**
- 옵션별 재고 확인 (option_id 기준)
- 쿠폰 유효성 검증
- 최종 결제금액 계산
- 잔액 검증

**Stage 2: Atomic Transaction**
```
BEGIN TRANSACTION
  UPDATE product_options (stock, version)
  UPDATE users (balance)
  INSERT orders, order_items
  UPDATE user_coupons (status)
  UPDATE products (status if needed)
COMMIT or ROLLBACK
```

**Stage 3: Async Outbox**
- INSERT outbox for external transmission
- Separate batch process handles async

### 쿠폰 발급 프로세스 (First-Come-First-Served)

```
SELECT coupons FOR UPDATE (pessimistic lock)
  Validate: is_active, valid period, remaining_qty > 0
  Check: UNIQUE(user_id, coupon_id)
UPDATE coupons: remaining_qty--, version++
INSERT user_coupons
```

### 동시성 제어

**낙관적 락** (주문 시 재고 차감):
- UPDATE with version check
- Mismatch → ERR-004 (retry)

**비관적 락** (쿠폰 발급):
- SELECT ... FOR UPDATE
- Blocks concurrent requests

---

## 부록 C: 시퀀스 다이어그램 맵핑

| Sequence Diagram | API Endpoint | Description |
|-----------------|--------------|-------------|
| 1. 상품 조회 | GET /products/{product_id} | 상품 옵션 조회 |
| 2. 장바구니 담기 | POST /carts/items | 옵션 선택하여 장바구니 추가 |
| 3. 주문 생성 (1단계) | POST /orders | 검증: 재고/잔액/쿠폰 |
| 3. 주문 생성 (2단계) | POST /orders | 거래: 원자적 처리 |
| 3. 주문 생성 (3단계) | POST /orders | 완료: Outbox 비동기 |
| 4. 동시 주문 처리 | POST /orders | 낙관적 락 Race Condition 방지 |
| 5. 쿠폰 발급 | POST /coupons/issue | 선착순 쿠폰 발급 |
| 7. 쿠폰 적용 주문 | POST /orders (coupon_id) | 쿠폰 적용하여 주문 |

---

## 부록 D: 데이터 타입 정의

| Type | Format | Example |
|------|--------|---------|
| Long | 64-bit integer | 1000 |
| Integer | 32-bit integer | 10 |
| String | UTF-8 text | "블랙/M" |
| Decimal | Floating-point | 0.10 |
| Boolean | true/false | true |
| Timestamp | ISO 8601 | "2025-10-29T12:45:00Z" |

**금액 단위**: 원(KRW), Long 타입 저장 (예: 29900 = 29,900원)
