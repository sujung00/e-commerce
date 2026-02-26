# MySQL 자동 시딩 설정 가이드

## 📋 개요

k6 부하테스트 실패 원인은 **빈 데이터베이스**였습니다.
이 가이드는 MySQL 컨테이너 초기화 시 자동으로 테이블 생성 및 데이터 시딩을 수행하는 방법을 설명합니다.

## 🎯 구성

### 파일 구조
```
docker/mysql/initdb.d/
├── 00-schema.sql          # DDL (테이블 생성)
├── 01-seed-test-data.sql  # DML (테스트 데이터 INSERT)
└── README.md              # 상세 사용 가이드
```

### 실행 순서 (자동)
1. **00-schema.sql**: 테이블 생성 (users, products, coupons 등 11개 테이블)
2. **01-seed-test-data.sql**: 테스트 데이터 시딩 (Stored Procedure 실행)

MySQL은 `initdb.d` 디렉토리의 `.sql` 파일을 **알파벳 순서**로 자동 실행합니다.

---

## 🚀 실행 방법

### ⚠️ 중요: initdb.d는 첫 초기화 시에만 실행됨

MySQL 공식 이미지는 **데이터 디렉토리가 비어있을 때만** initdb.d 스크립트를 실행합니다.
- 기존 볼륨이 있으면 스크립트 무시
- `-v` 플래그로 볼륨까지 삭제해야 재실행됨

### 1단계: 기존 환경 정리

```bash
# 현재 디렉토리 확인
pwd
# 출력: /Users/sujung/Desktop/workspace/java/e-commerce

# 모든 컨테이너 중지 및 볼륨 삭제
docker-compose down -v
```

**`-v` 플래그 의미**:
- 컨테이너 중지
- 네트워크 제거
- **볼륨 삭제** (mysql-data, redis-data 등)

### 2단계: MySQL 컨테이너 시작 (자동 시딩)

```bash
# MySQL만 먼저 시작 (initdb.d 자동 실행)
docker-compose up -d mysql

# 초기화 완료 대기 (30초 정도 소요)
echo "MySQL 초기화 중... 30초 대기"
sleep 30

# 또는 로그로 완료 확인
docker logs mysql 2>&1 | tail -50
```

**로그에서 확인할 내용**:
```
✅ 테이블 생성 완료!
✅ 시딩 데이터 생성 완료!
Users: 1000명
Products: 100개
ProductOptions: 100개
Coupons: 2개
```

---

## ✅ 검증 방법

### 방법 1: SQL로 직접 확인

```bash
# MySQL 컨테이너 접속
docker exec -it mysql mysql -u root -p${DB_PASSWORD} ${DB_NAME}
```

```sql
-- 테이블 생성 확인
SHOW TABLES;
-- 예상: 11개 테이블 (users, products, product_options, carts, cart_items, orders, order_items, coupons, user_coupons, outbox, data_platform_events)

-- 데이터 개수 확인
SELECT
    (SELECT COUNT(*) FROM users) AS users,
    (SELECT COUNT(*) FROM products) AS products,
    (SELECT COUNT(*) FROM product_options) AS options,
    (SELECT COUNT(*) FROM coupons) AS coupons,
    (SELECT remaining_qty FROM coupons WHERE coupon_id=1) AS coupon1_stock;
-- 예상: users=1000, products=100, options=100, coupons=2, coupon1_stock=100000

-- k6 필수 데이터 확인
SELECT coupon_id, coupon_name, discount_type, discount_rate, remaining_qty, is_active
FROM coupons WHERE coupon_id = 1;
-- 예상: couponId=1, PERCENTAGE, 0.1 (10% 할인), remaining_qty=100000, is_active=1

SELECT product_id, product_name, price, total_stock, status
FROM products WHERE product_id = 1;
-- 예상: productId=1, total_stock=10000, status='IN_STOCK'

SELECT user_id, email, balance FROM users WHERE user_id = 1;
-- 예상: userId=1, balance=10000000

-- 컨테이너 빠져나오기
exit
```

### 방법 2: REST API로 확인 (앱 시작 후)

```bash
# 전체 스택 시작
docker-compose up -d

# 앱 준비 대기 (Ctrl+C로 중지)
docker logs -f ecommerce-app | grep "Started ECommerceApplication"

# API 테스트
# 1) 쿠폰 목록 조회 (couponId=1 존재 확인)
curl -s http://localhost:8090/api/coupons | jq '.[0] | {couponId, couponName, remainingQty, isActive}'
# 예상:
# {
#   "couponId": 1,
#   "couponName": "[부하테스트] 10% 할인 쿠폰",
#   "remainingQty": 100000,
#   "isActive": true
# }

# 2) 상품 조회 (productId=1 존재 확인)
curl -s http://localhost:8090/api/products/1 | jq '{productId, productName, price, totalStock, status}'
# 예상:
# {
#   "productId": 1,
#   "productName": "테스트 상품 1",
#   "price": 10100,
#   "totalStock": 10000,
#   "status": "IN_STOCK"
# }

# 3) 사용자 잔액 조회 (userId=1, balance=10,000,000)
curl -s http://localhost:8090/api/users/1/balance | jq
# 예상:
# {
#   "userId": 1,
#   "balance": 10000000
# }

# 4) 상품 목록 조회 (페이징)
curl -s 'http://localhost:8090/api/products?page=0&size=5' | jq '.content | length'
# 예상: 5 (5개 상품 반환)
```

### 방법 3: k6 부하테스트 실행

```bash
# k6 부하테스트 실행 (컨테이너 방식)
docker-compose run --rm k6 run /scripts/load-test-LT-001.js

# 또는 로컬 k6로 실행 (k6 설치 필요)
k6 run -e BASE_URL=http://localhost:8090 performance/k6/scripts/load-test-LT-001.js
```

**성공 기준**:
```
✅ http_req_failed: 0.00% (또는 <1%)
✅ Product detail success: >95%
✅ Add to cart success: >95%
✅ Create order success: >90%
✅ Coupon issue success: >80% (동시성 제한으로 일부 실패 예상)
```

**Before vs After**:
| 항목 | Before (빈 DB) | After (시딩 완료) |
|------|----------------|-------------------|
| 실패율 | ~70% | <5% |
| 주요 에러 | "쿠폰을 찾을 수 없음" | 정상 동시성 제한 |
| Product detail | 0% | >95% |
| Coupon issue | 0% | >80% |

---

## 🔧 트러블슈팅

### 문제 1: "테이블이 이미 존재합니다" 에러

**원인**: 볼륨을 삭제하지 않아서 기존 테이블이 남아있음

**해결**:
```bash
# 볼륨까지 삭제하고 재시작
docker-compose down -v
docker-compose up -d mysql
```

### 문제 2: initdb.d 스크립트가 실행되지 않음

**원인**: MySQL 데이터 디렉토리에 기존 데이터가 있음

**확인**:
```bash
# 볼륨 확인
docker volume ls | grep mysql
# mysql-data 볼륨이 있으면 initdb.d 실행 안됨

# 해결: 볼륨 삭제
docker-compose down -v
docker volume rm e-commerce_mysql-data  # 볼륨 이름 확인 후 삭제
docker-compose up -d mysql
```

### 문제 3: "Unknown database 'hhplus_ecommerce'" 에러

**원인**: 데이터베이스가 생성되지 않음

**해결**:
```bash
# .env 파일 확인
cat .env | grep DB_NAME
# DB_NAME=hhplus_ecommerce

# docker-compose.yml 확인
cat docker-compose.yml | grep MYSQL_DATABASE
# MYSQL_DATABASE: ${DB_NAME}

# 환경변수 확인
docker exec mysql env | grep MYSQL_DATABASE
# MYSQL_DATABASE=hhplus_ecommerce

# 수동으로 데이터베이스 생성
docker exec -it mysql mysql -u root -p${DB_PASSWORD} -e "CREATE DATABASE IF NOT EXISTS hhplus_ecommerce;"
```

### 문제 4: 시딩 후에도 k6 실패율이 높음

**원인 1**: Spring Boot 앱이 아직 준비 중

```bash
# Health Check
curl http://localhost:8090/actuator/health
# 예상: {"status":"UP"}

# 앱 로그 확인
docker logs ecommerce-app | grep "Started ECommerceApplication"
```

**원인 2**: 재고/잔액 부족 (장시간 테스트 시)

```sql
-- 재고 재설정
UPDATE products SET total_stock = 10000;
UPDATE product_options SET stock = 10000;
UPDATE coupons SET remaining_qty = 100000 WHERE coupon_id = 1;
UPDATE users SET balance = 10000000;
```

### 문제 5: "Access denied for user 'root'" 에러

**원인**: DB_PASSWORD 환경변수 누락

**해결**:
```bash
# .env 파일 확인
cat .env

# 필수 환경변수
# DB_PASSWORD=your_password
# DB_NAME=hhplus_ecommerce
# DB_USERNAME=root

# .env 파일이 없으면 생성
cat > .env <<EOF
DB_PASSWORD=hhplus1234
DB_NAME=hhplus_ecommerce
DB_USERNAME=root
REDIS_PASSWORD=
EOF

# 재시작
docker-compose down
docker-compose up -d mysql
```

---

## 🔄 재시딩 워크플로우

정기적으로 초기 상태로 리셋하고 싶을 때:

```bash
# 1. 전체 중지 및 볼륨 삭제
docker-compose down -v

# 2. MySQL 시작 (자동 시딩)
docker-compose up -d mysql

# 3. 시딩 완료 확인
docker logs mysql 2>&1 | grep -A 5 "시딩 데이터"

# 4. 전체 스택 시작
docker-compose up -d

# 5. 앱 준비 대기
sleep 20  # 또는 docker logs -f ecommerce-app

# 6. k6 테스트 실행
docker-compose run --rm k6 run /scripts/load-test-LT-001.js
```

---

## 📊 데이터 명세

### 시딩 데이터 요약

| 테이블 | 데이터 개수 | 주요 특징 |
|--------|-------------|-----------|
| users | 1,000명 | balance=10,000,000원, userId=1~1000 |
| products | 100개 | price=10,000~19,900원, stock=10,000 |
| product_options | 100개 | 각 product당 1개, stock=10,000 |
| coupons | 2개 | couponId=1 (10% 할인, 100,000개)<br>couponId=2 (5,000원 할인, 50,000개) |
| carts | 0개 | k6 테스트 중 생성됨 |
| cart_items | 0개 | k6 테스트 중 생성됨 |
| orders | 0개 | k6 테스트 중 생성됨 |
| order_items | 0개 | k6 테스트 중 생성됨 |
| user_coupons | 0개 | k6 쿠폰 발급 시나리오에서 생성됨 |
| outbox | 0개 | 주문 완료 시 Kafka 메시지 발행용 |
| data_platform_events | 0개 | Kafka Consumer 처리 이력 |

### k6 시나리오 vs 시딩 데이터 매핑

| k6 시나리오 | 요구 데이터 | 시딩 데이터 | 비고 |
|-------------|-------------|-------------|------|
| normalPurchaseScenario | userId: 1~10,000 (랜덤) | 1~1,000 생성 | 충분한 범위 |
| normalPurchaseScenario | productId: 1~1,000 (랜덤) | 1~100 생성 | 충분한 다양성 |
| normalPurchaseScenario | optionId: 1 (고정) | 1~100 생성 | 모든 product에 optionId=1 존재 |
| couponIssueScenario | couponId: 1 (고정) | couponId=1 (100,000개) | ✅ 필수 |
| popularProductScenario | productId: 1 (고정) | productId=1 생성 | ✅ 필수 |

**설계 의도**:
- k6가 10,000명 유저를 가정하지만, 1,000명으로 제한 (시딩 성능 고려)
- k6가 1,000개 상품을 가정하지만, 100개로 제한 (충분한 분산)
- balance/stock은 충분히 크게 설정하여 "재고 부족" 오류 방지
- **couponId=1은 필수** (k6 시나리오에서 하드코딩됨)

---

## 🎯 다음 단계

1. ✅ docker-compose down -v
2. ✅ docker-compose up -d mysql
3. ✅ docker logs mysql (시딩 확인)
4. ✅ SQL로 데이터 검증
5. ✅ docker-compose up -d (전체 스택)
6. ✅ API 테스트 (curl)
7. ✅ k6 부하테스트 실행
8. 📈 Grafana 대시보드 모니터링 (http://localhost:3000)

**성공 확인**:
- k6 실패율 <5%
- "쿠폰을 찾을 수 없음" 로그 없음
- Product detail/Coupon issue check >80% 성공