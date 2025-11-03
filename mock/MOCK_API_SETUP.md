# JSON Server Mock API 설정 및 실행 가이드

## 빠른 시작

### 1단계: JSON Server 설치

```bash
npm install json-server --save-dev
```

또는 전역 설치:

```bash
npm install -g json-server
```

### 2단계: 서버 실행

```bash
json-server --watch db.json --port 3000
```

또는 커스텀 라우팅 사용:

```bash
json-server --watch db.json --routes routes.json --port 3000
```

서버가 시작되면 다음과 같이 표시됩니다:

```
  ⌨️  Server started at http://localhost:3000
  📄 Db at db.json

  💬 Use the following command to install dependencies if needed
  npm install json-server
```

---

## 프로젝트 설정

### package.json에 스크립트 추가

프로젝트의 `package.json` 파일에 다음 스크립트를 추가합니다:

```json
{
  "scripts": {
    "mock-api": "json-server --watch db.json --port 3000",
    "mock-api:custom": "json-server --watch db.json --routes routes.json --port 3000",
    "mock-api:middleware": "json-server --watch db.json --port 3000 --middlewares ./middleware.js"
  },
  "devDependencies": {
    "json-server": "^0.17.0"
  }
}
```

그리고 다음 명령으로 실행합니다:

```bash
npm run mock-api
```

---

## 라우팅 설정 (선택사항)

`/api` 프리픽스를 사용하려면 `routes.json` 파일을 생성합니다:

```json
{
  "/api/*": "/$1"
}
```

이제 모든 API 요청이 `/api` 프리픽스를 지원합니다:

```bash
curl http://localhost:3000/api/products
```

---

## 미들웨어 설정 (선택사항)

`middleware.js` 파일을 생성하여 CORS 및 로깅을 설정할 수 있습니다:

```javascript
module.exports = (req, res, next) => {
  // CORS 설정
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  // OPTIONS 요청 처리
  if (req.method === 'OPTIONS') {
    res.sendStatus(200);
    return;
  }

  // 요청 로깅
  console.log(`${new Date().toISOString()} - ${req.method} ${req.path}`);

  next();
};
```

---

## 실시간 데이터 업데이트

JSON Server는 `db.json` 파일을 감시하고 있습니다. 파일이 변경되면 자동으로 리로드됩니다.

### 프로그래매틱 데이터 추가 (Node.js)

```javascript
const fs = require('fs');

// db.json 읽기
const db = JSON.parse(fs.readFileSync('db.json', 'utf8'));

// 새 상품 추가
db.products.push({
  product_id: 6,
  product_name: "새로운 상품",
  description: "설명",
  price: 50000,
  total_stock: 100,
  status: "판매 중",
  created_at: new Date().toISOString()
});

// 저장
fs.writeFileSync('db.json', JSON.stringify(db, null, 2));
```

---

## API 테스트 방법

### 1. Postman 사용

1. Postman을 실행합니다.
2. 메뉴에서 "Import"를 클릭합니다.
3. `postman_collection.json` 파일을 선택합니다.
4. 컬렉션이 임포트되면, 각 요청을 실행할 수 있습니다.

**변수 설정**:
- `base_url`: `http://localhost:3000` (기본값)
- `/api` 프리픽스 사용 시: `http://localhost:3000/api`

### 2. cURL 사용

```bash
# 모든 상품 조회
curl -X GET "http://localhost:3000/products"

# 특정 상품 조회
curl -X GET "http://localhost:3000/products/1"

# 장바구니 아이템 추가
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

### 3. JavaScript Fetch API

```javascript
const API_BASE = 'http://localhost:3000';

// GET 요청
async function getProducts() {
  const response = await fetch(`${API_BASE}/products`);
  return response.json();
}

// POST 요청
async function createOrder(order) {
  const response = await fetch(`${API_BASE}/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(order)
  });
  return response.json();
}

// PUT 요청
async function updateCartItem(itemId, quantity) {
  const response = await fetch(`${API_BASE}/cart_items/${itemId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ quantity, subtotal: quantity * 29900 })
  });
  return response.json();
}

// DELETE 요청
async function removeCartItem(itemId) {
  await fetch(`${API_BASE}/cart_items/${itemId}`, {
    method: 'DELETE'
  });
}
```

---

## 주요 쿼리 파라미터

| 파라미터 | 설명 | 예시 |
|---------|------|------|
| `_page` | 페이지 (기본: 1) | `?_page=2` |
| `_limit` | 페이지 크기 | `?_limit=20` |
| `_sort` | 정렬 필드 | `?_sort=product_id` |
| `_order` | 정렬 순서 (asc\|desc) | `?_order=desc` |
| `필드=값` | 필드 필터링 | `?status=판매중` |
| `필드_gte` | >= 비교 | `?price_gte=10000` |
| `필드_lte` | <= 비교 | `?price_lte=100000` |
| `필드_ne` | != 비교 | `?status_ne=품절` |
| `필드_like` | 정규식 포함 | `?product_name_like=셔츠` |

### 사용 예시

```bash
# 가격이 30,000 이상이고 판매 중인 상품 (내림차순)
curl "http://localhost:3000/products?price_gte=30000&status=판매중&_sort=price&_order=desc"

# 사용자 100의 활성 쿠폰 조회
curl "http://localhost:3000/user_coupons?user_id=100&status=ACTIVE"

# 완료된 주문 목록 (최근순)
curl "http://localhost:3000/orders?order_status=COMPLETED&_sort=created_at&_order=desc&_limit=5"
```

---

## 트러블슈팅

### 포트 이미 사용 중

다른 포트 사용:

```bash
json-server --watch db.json --port 8080
```

### 다른 포트에서 실행 중인 서버에 접근하는 경우

Postman 또는 API 클라이언트에서 `base_url` 변수를 수정합니다:

```javascript
const API_BASE = 'http://localhost:8080';
```

### CORS 에러

미들웨어를 활용하여 CORS 설정:

```bash
json-server --watch db.json --port 3000 --middlewares ./middleware.js
```

### JSON 형식 오류

`db.json` 파일의 JSON 형식을 확인합니다:

```bash
node -e "console.log(JSON.parse(require('fs').readFileSync('db.json', 'utf8')))"
```

---

## 데이터 리셋

`db.json` 파일을 원본으로 복원하려면:

```bash
# Git을 사용하는 경우
git checkout db.json

# 또는 백업에서 복원
cp db.json.backup db.json
```

---

## 프론트엔드 통합 예시

### React 예시

```javascript
import { useEffect, useState } from 'react';

const API_BASE = 'http://localhost:3000';

function ProductList() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch(`${API_BASE}/products?_limit=10`)
      .then(res => res.json())
      .then(data => {
        setProducts(data);
        setLoading(false);
      });
  }, []);

  if (loading) return <div>Loading...</div>;

  return (
    <div>
      {products.map(product => (
        <div key={product.product_id}>
          <h3>{product.product_name}</h3>
          <p>가격: {product.price.toLocaleString()}원</p>
          <p>재고: {product.total_stock}</p>
        </div>
      ))}
    </div>
  );
}

export default ProductList;
```

### Vue.js 예시

```javascript
import { ref, onMounted } from 'vue';

const API_BASE = 'http://localhost:3000';

export default {
  setup() {
    const products = ref([]);
    const loading = ref(true);

    onMounted(async () => {
      const response = await fetch(`${API_BASE}/products?_limit=10`);
      products.value = await response.json();
      loading.value = false;
    });

    return { products, loading };
  }
};
```

---

## 성능 최적화

### 대량 데이터 조회 최소화

```bash
# 나쁜 예: 모든 데이터 조회
curl "http://localhost:3000/products"

# 좋은 예: 필요한 페이지만 조회
curl "http://localhost:3000/products?_page=1&_limit=10"
```

### 정렬 및 필터링 활용

```bash
# 서버 사이드에서 정렬/필터링하여 네트워크 트래픽 감소
curl "http://localhost:3000/products?price_gte=10000&_sort=price&_limit=20"
```

---

## 프로덕션 전환

실제 백엔드로 전환할 때:

1. **기존 Mock API를 실제 API URL로 변경**:

```javascript
// 개발 환경
const API_BASE = process.env.NODE_ENV === 'development'
  ? 'http://localhost:3000'
  : 'https://api.example.com';
```

2. **db.json 데이터를 데이터베이스로 마이그레이션**

3. **실제 비즈니스 로직 구현**:
   - 트랜잭션 처리
   - 동시성 제어
   - 검증 로직
   - 에러 핸들링

---

## 참고 자료

- [JSON Server 공식 문서](https://github.com/typicode/json-server)
- [API 명세서](../docs/api/api-specification.md)
- [JSON Server 가이드](JSON_SERVER_GUIDE.md)
- [데이터 모델](./docs/data-models.md)

---

## 추가 팁

### 데이터베이스 초기화

```bash
# db.json을 원본 상태로 리셋
npm run reset-db  # (package.json에 스크립트 추가 필요)
```

### 자동 ID 생성

JSON Server는 `id` 필드에 대해 자동으로 증가하는 값을 생성합니다.

```bash
# 새 상품 생성 (ID는 자동 생성)
curl -X POST "http://localhost:3000/products" \
  -H "Content-Type: application/json" \
  -d '{
    "product_name": "새 상품",
    "description": "설명",
    "price": 50000,
    "total_stock": 100,
    "status": "판매 중",
    "created_at": "2025-10-29T16:00:00Z"
  }'
```

### 전체 관계 조회 (1:N)

```bash
# 상품과 옵션을 함께 조회
curl "http://localhost:3000/products/1?_expand=product_options"
```

**주의**: 이 기능을 사용하려면 `db.json`에 명시적 관계를 정의해야 합니다.
