# k6 부하테스트용 데이터베이스 시딩 가이드

## 개요

k6 부하테스트가 실패하는 주요 원인은 MySQL 데이터베이스에 테스트 데이터가 없기 때문입니다.
이 디렉토리의 `01-seed-test-data.sql` 스크립트는 k6 시나리오가 요구하는 모든 데이터를 자동으로 생성합니다.

## 시딩 데이터 명세

### k6 스크립트 분석 결과 (load-test-LT-001.js)

| 엔티티 | k6 요구사항 | 시딩 데이터 |
|--------|-------------|-------------|
| **Users** | userId: 1~10,000 (랜덤) | 1~1,000명 생성 (balance: 10,000,000원) |
| **Products** | productId: 1~1,000 (랜덤)<br>popularProductId: 1 (고정) | 1~100개 생성 (price: 10,000~19,900원, stock: 10,000) |
| **ProductOptions** | optionId: 1 (고정) | 1~100개 생성 (각 product당 1개, stock: 10,000) |
| **Coupons** | couponId: 1 (고정) | couponId=1: PERCENTAGE 10% 할인, 100,000개<br>couponId=2: FIXED_AMOUNT 5,000원 할인, 50,000개 |

**성능 최적화 참고**:
- k6가 10,000명의 유저를 요구하지만, 1,000명으로 제한 (충분한 범위 + 시딩 성능 고려)
- k6가 1,000개 상품을 요구하지만, 100개로 제한 (충분한 다양성 + 시딩 성능 고려)
- 각 엔티티의 balance/stock은 충분히 크게 설정하여 재고 부족 오류 방지

---

## 🚀 실행 방법

### 방법 1: 자동 시딩 (권장)

MySQL 컨테이너를 **처음 생성**할 때 자동으로 실행됩니다.

```bash
# 1. 기존 MySQL 컨테이너와 볼륨 삭제 (데이터 초기화)
docker-compose down -v

# 2. MySQL 컨테이너 재생성 (initdb.d 자동 실행)
docker-compose up -d mysql

# 3. MySQL 초기화 로그 확인 (시딩 성공 메시지 확인)
docker logs mysql 2>&1 | grep -A 10 "시딩 데이터"
```

**중요**: `initdb.d` 스크립트는 **MySQL 데이터 디렉토리가 비어있을 때만** 실행됩니다.
- 기존 볼륨이 있으면 실행되지 않음
- `-v` 플래그로 볼륨까지 삭제해야 재실행됨

### 방법 2: 수동 시딩 (이미 MySQL 컨테이너가 있는 경우)

```bash
# 1. MySQL 컨테이너에 접속
docker exec -it mysql mysql -u root -p${DB_PASSWORD} ${DB_NAME}

# 2. SQL 스크립트 직접 실행
mysql> source /docker-entrypoint-initdb.d/01-seed-test-data.sql;

# 또는 호스트에서 직접 실행
docker exec -i mysql mysql -u root -p${DB_PASSWORD} ${DB_NAME} < docker/mysql/initdb.d/01-seed-test-data.sql
```

---

## ✅ 검증 방법

### 1. SQL로 데이터 확인

```bash
# MySQL 컨테이너에 접속
docker exec -it mysql mysql -u root -p${DB_PASSWORD} ${DB_NAME}
```

```sql
-- 전체 데이터 요약
SELECT
    '데이터 요약' AS summary,
    (SELECT COUNT(*) FROM users) AS users,
    (SELECT COUNT(*) FROM products) AS products,
    (SELECT COUNT(*) FROM product_options) AS product_options,
    (SELECT COUNT(*) FROM coupons) AS coupons,
    (SELECT SUM(remaining_qty) FROM coupons) AS coupon_stock;

-- 샘플 데이터 확인
SELECT user_id, email, name, balance FROM users LIMIT 3;
SELECT product_id, product_name, price, total_stock, status FROM products LIMIT 3;
SELECT option_id, product_id, name, stock FROM product_options LIMIT 3;
SELECT coupon_id, coupon_name, discount_type, discount_amount, discount_rate, remaining_qty, is_active FROM coupons;
```

**예상 결과**:
```
+----------+-------+----------+-----------------+---------+--------------+
| summary  | users | products | product_options | coupons | coupon_stock |
+----------+-------+----------+-----------------+---------+--------------+
| 데이터 요약 |  1000 |      100 |             100 |       2 |       150000 |
+----------+-------+----------+-----------------+---------+--------------+
```

### 2. REST API로 확인 (Spring Boot 앱 실행 중)

```bash
# Spring Boot 앱 시작 (MySQL 의존성)
docker-compose up -d app

# 앱 로그 확인 (시작 완료 대기)
docker logs -f ecommerce-app

# API 테스트
# 1) 상품 목록 조회 (productId=1 존재 확인)
curl -s http://localhost:8090/api/products?page=0&size=5 | jq

# 2) 특정 상품 상세 조회 (popularProductId=1)
curl -s http://localhost:8090/api/products/1 | jq

# 3) 쿠폰 목록 조회 (couponId=1 존재 확인)
curl -s http://localhost:8090/api/coupons | jq

# 4) 사용자 잔액 조회 (userId=1, balance=10,000,000 확인)
curl -s http://localhost:8090/api/users/1/balance | jq
```

**예상 응답**:
```json
// GET /api/products/1
{
  "productId": 1,
  "productName": "테스트 상품 1",
  "price": 10100,
  "totalStock": 10000,
  "status": "IN_STOCK"
}

// GET /api/coupons
[
  {
    "couponId": 1,
    "couponName": "[부하테스트] 10% 할인 쿠폰",
    "discountType": "PERCENTAGE",
    "discountRate": 0.1,
    "remainingQty": 100000,
    "isActive": true
  }
]

// GET /api/users/1/balance
{
  "userId": 1,
  "balance": 10000000
}
```

### 3. k6 부하테스트 재실행

```bash
# k6 컨테이너로 부하테스트 실행
docker-compose run --rm k6 run /scripts/load-test-LT-001.js

# 또는 로컬에서 실행 (k6 설치 필요)
k6 run -e BASE_URL=http://localhost:8090 performance/k6/scripts/load-test-LT-001.js
```

**성공 기준**:
- ✅ `http_req_failed` 비율: 0% (또는 매우 낮은 비율, <1%)
- ✅ `check` 성공률:
  - `Product detail success`: >95%
  - `Add to cart success`: >95%
  - `Create order success`: >90%
  - `Coupon issue success`: >80% (동시성 제한으로 일부 실패 가능)
- ✅ `[CouponService] 쿠폰을 찾을 수 없음` 로그 없음

---

## 🔧 트러블슈팅

### 문제 1: initdb.d 스크립트가 실행되지 않음

**원인**: MySQL 볼륨에 기존 데이터가 있음

**해결**:
```bash
# 볼륨까지 삭제하고 재생성
docker-compose down -v
docker-compose up -d mysql
docker logs mysql 2>&1 | grep "시딩 데이터"
```

### 문제 2: 시딩 후에도 k6 실패율이 높음

**원인 1**: Spring Boot 앱이 아직 시작 중이거나 MySQL 연결 실패

```bash
# 앱 로그 확인
docker logs ecommerce-app

# 앱 Health Check
curl http://localhost:8090/actuator/health
```

**원인 2**: 재고 부족 (장시간 테스트 시)

```sql
-- 재고 확인
SELECT product_id, total_stock FROM products WHERE total_stock < 1000;
SELECT option_id, stock FROM product_options WHERE stock < 1000;
SELECT coupon_id, remaining_qty FROM coupons WHERE remaining_qty < 1000;

-- 재고 재설정 (필요 시)
UPDATE products SET total_stock = 10000;
UPDATE product_options SET stock = 10000;
UPDATE coupons SET remaining_qty = 100000 WHERE coupon_id = 1;
```

**원인 3**: 잔액 부족

```sql
-- 잔액 확인
SELECT user_id, balance FROM users WHERE balance < 100000 LIMIT 10;

-- 잔액 재설정 (필요 시)
UPDATE users SET balance = 10000000;
```

### 문제 3: "Duplicate entry" 에러 발생

**원인**: `INSERT IGNORE` 로 이미 처리되지만, 테이블에 기존 데이터가 있을 수 있음

**해결**:
```bash
# 특정 테이블만 초기화하고 재시딩
docker exec -it mysql mysql -u root -p${DB_PASSWORD} ${DB_NAME}

# 테이블 비우기 (외래키 제약 고려)
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE user_issued_coupons;
TRUNCATE TABLE order_items;
TRUNCATE TABLE orders;
TRUNCATE TABLE cart_items;
TRUNCATE TABLE carts;
TRUNCATE TABLE product_options;
TRUNCATE TABLE products;
TRUNCATE TABLE coupons;
TRUNCATE TABLE users;
SET FOREIGN_KEY_CHECKS = 1;

# 재시딩
source /docker-entrypoint-initdb.d/01-seed-test-data.sql;
```

---

## 📊 성능 벤치마크

**시딩 시간** (MacBook M1, Docker Desktop):
- Users 1,000명: ~2초
- Products 100개: ~1초
- ProductOptions 100개: ~1초
- Coupons 2개: <1초
- **전체**: ~5초

**데이터 크기**:
- Users: ~100KB
- Products: ~20KB
- ProductOptions: ~15KB
- Coupons: ~2KB
- **전체**: ~137KB

---

## 🔄 재시딩 워크플로우

정기적으로 테스트 데이터를 초기화하고 싶을 때:

```bash
# 1. 전체 스택 중지 및 볼륨 삭제
docker-compose down -v

# 2. MySQL만 먼저 시작 (시딩 자동 실행)
docker-compose up -d mysql

# 3. 시딩 완료 대기 (로그 확인)
docker logs mysql 2>&1 | tail -20

# 4. 전체 스택 시작
docker-compose up -d

# 5. 앱 준비 대기
docker logs -f ecommerce-app | grep "Started ECommerceApplication"

# 6. k6 테스트 실행
docker-compose run --rm k6 run /scripts/load-test-LT-001.js
```

---

## 📝 참고

- **Idempotent**: `INSERT IGNORE` 사용으로 여러 번 실행해도 안전
- **Performance**: Stored Procedure로 대량 데이터 생성 최적화
- **Realistic Data**: 실제 k6 시나리오와 정확히 일치하는 데이터 범위
- **Extensibility**: 추가 시나리오가 필요하면 SQL 파일에 INSERT 구문 추가

---

## 🎯 다음 단계

1. ✅ 데이터 시딩 완료
2. ✅ API 테스트로 데이터 존재 확인
3. ✅ k6 부하테스트 실행 및 성공률 확인
4. 📈 Grafana 대시보드에서 메트릭 모니터링 (http://localhost:3000)
5. 🔧 필요 시 시나리오 튜닝 및 재테스트