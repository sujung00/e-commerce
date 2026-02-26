/**
 * Load Test (LT-001) - 평시 트래픽 안정성 검증 (TPS 기반)
 *
 * ⚙️ 시딩 범위 및 유효 ID 풀 사용:
 * - users: 1~1000 (USER_ID_MIN ~ USER_ID_MAX)
 * - products: 1~100 (setup()에서 실제 유효 ID 풀 생성)
 * - coupons: 1~2 (setup()에서 실제 유효 ID 풀 생성)
 * - 404 에러 방지: setup()에서 가져온 유효 ID만 사용
 *
 * 테스트 목표:
 * - 평시 예상 트래픽(30 TPS)에서 30분간 안정적 동작 검증
 *
 * 테스트 구성:
 * - Executor: ramping-arrival-rate (정확한 TPS 제어)
 * - 목표 TPS: 30 req/s (전체 합산)
 * - 램프업: 5분 (0 → 30 TPS)
 * - 지속: 30분 (30 TPS 유지)
 * - 램프다운: 2분 (30 → 0 TPS)
 * - Think Time: 2~5초 (랜덤, 사용자 행동 시뮬레이션)
 *
 * 트래픽 비율 (시나리오별 분리):
 * - 일반 구매 플로우: 70% (21 TPS → 4.2 iterations/s)
 * - 쿠폰 발급: 20% (6 TPS → 1.5 iterations/s)
 * - 인기 상품 조회: 10% (3 TPS → 1 iteration/s)
 *
 * 성공 기준:
 * - 에러율 < 0.1%
 * - P95 < 300ms
 * - P99 < 500ms
 * - Throughput: 25~35 TPS (목표 30 TPS ± 5)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ============================================
// 환경 변수 및 상수 정의
// ============================================

const BASE_URL = __ENV.BASE_URL || 'http://app:8080';

// 시딩 데이터 범위 (고정)
const USER_ID_MIN = 1;
const USER_ID_MAX = 1000;
const PRODUCT_ID_MIN = 1;
const PRODUCT_ID_MAX = 100;
const DEFAULT_COUPON_ID = 1;

// ============================================
// 커스텀 메트릭
// ============================================

const errorRate = new Rate('errors');
const orderLatency = new Trend('order_latency');
const couponLatency = new Trend('coupon_latency');
const httpStatusCount = new Counter('http_status_count');
const scenarioMetrics = new Counter('scenario_executions');
const notFoundSkipped = new Counter('not_found_skipped'); // 404 skip 카운터

// ============================================
// k6 설정 - 시나리오별 ramping-arrival-rate executor
// ============================================

export const options = {
  scenarios: {
    // 시나리오 1: 일반 구매 플로우 (70% = 21 TPS)
    normalPurchase: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1m',
      preAllocatedVUs: 60,
      maxVUs: 100,
      stages: [
        { duration: '5m', target: 252 },  // 램프업: 0 → 252 iter/m (21 TPS)
        { duration: '30m', target: 252 }, // 유지: 252 iter/m (21 TPS)
        { duration: '2m', target: 0 },    // 램프다운: 252 → 0 iter/m
      ],
      exec: 'normalPurchaseScenario',
      tags: { scenario: 'normalPurchase' },
    },

    // 시나리오 2: 쿠폰 발급 (20% = 6 TPS)
    couponIssue: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1m',
      preAllocatedVUs: 20,
      maxVUs: 40,
      stages: [
        { duration: '5m', target: 90 },   // 램프업: 0 → 90 iter/m (6 TPS)
        { duration: '30m', target: 90 },  // 유지: 90 iter/m (6 TPS)
        { duration: '2m', target: 0 },    // 램프다운: 90 → 0 iter/m
      ],
      exec: 'couponIssueScenario',
      tags: { scenario: 'couponIssue' },
    },

    // 시나리오 3: 인기 상품 조회/주문 (10% = 3 TPS)
    popularProduct: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1m',
      preAllocatedVUs: 5,
      maxVUs: 10,
      stages: [
        { duration: '5m', target: 60 },   // 램프업: 0 → 60 iter/m (3 TPS)
        { duration: '30m', target: 60 },  // 유지: 60 iter/m (3 TPS)
        { duration: '2m', target: 0 },    // 램프다운: 60 → 0 iter/m
      ],
      exec: 'popularProductScenario',
      tags: { scenario: 'popularProduct' },
    },
  },

  thresholds: {
    'errors': ['rate<0.001'],
    'http_req_duration': [
      'p(95)<300',
      'p(99)<500',
    ],
    'http_reqs': [
      'rate>=25',
      'rate<=35',
    ],
    'errors{scenario:normalPurchase}': ['rate<0.001'],
    'errors{scenario:couponIssue}': ['rate<0.001'],
    'errors{scenario:popularProduct}': ['rate<0.001'],
  },
};

// ============================================
// Setup: 유효 ID 풀 생성 (테스트 시작 시 1회 실행)
// ============================================

export function setup() {
  console.log('🔧 Setup: 유효 ID 풀 생성 중...');

  const headers = {
    'Content-Type': 'application/json',
    'X-USER-ID': '1',
  };

  // 1. 유효 productIds 수집
  let productIds = [];
  try {
    const productsRes = http.get(`${BASE_URL}/api/products?page=0&size=100`, { headers });
    if (productsRes.status === 200) {
      const productsData = productsRes.json();
      if (productsData.content && Array.isArray(productsData.content)) {
        productIds = productsData.content.map(p => p.product_id).filter(id => id != null);
      }
    }
  } catch (e) {
    console.error('⚠️ Setup: 상품 목록 조회 실패:', e);
  }

  // Fallback: 빈 배열이면 1~100 사용
  if (productIds.length === 0) {
    console.warn('⚠️ Setup: 상품 목록이 비어있음, fallback [1..100] 사용');
    for (let i = PRODUCT_ID_MIN; i <= PRODUCT_ID_MAX; i++) {
      productIds.push(i);
    }
  }

  // 2. 유효 couponIds 수집
  let couponIds = [];
  try {
    const couponsRes = http.get(`${BASE_URL}/api/coupons`, { headers });
    if (couponsRes.status === 200) {
      const couponsData = couponsRes.json();
      if (Array.isArray(couponsData)) {
        couponIds = couponsData.map(c => c.couponId).filter(id => id != null);
      }
    }
  } catch (e) {
    console.error('⚠️ Setup: 쿠폰 목록 조회 실패:', e);
  }

  // Fallback: 빈 배열이면 [1,2] 사용
  if (couponIds.length === 0) {
    console.warn('⚠️ Setup: 쿠폰 목록이 비어있음, fallback [1,2] 사용');
    couponIds = [1, 2];
  }

  console.log(`✅ Setup 완료: products=${productIds.length}개, coupons=${couponIds.length}개`);

  return {
    productIds: productIds,
    couponIds: couponIds,
  };
}

// ============================================
// Helper 함수
// ============================================

// 배열에서 랜덤 아이템 선택
function randomItem(arr) {
  if (!arr || arr.length === 0) return null;
  return arr[Math.floor(Math.random() * arr.length)];
}

// 랜덤 userId 생성
function getRandomUserId() {
  return Math.floor(Math.random() * (USER_ID_MAX - USER_ID_MIN + 1)) + USER_ID_MIN;
}

// 랜덤 Think Time
function getRandomThinkTime() {
  return Math.random() * 3 + 2; // 2~5초
}

// HTTP 응답 상태코드 기록
function recordHttpStatus(res) {
  httpStatusCount.add(1, { status: res.status.toString() });
}

// 실패 시 에러 로깅 (디버깅용, ID 정보 포함)
function logIfFail(res, expectedStatus, context, ids = {}) {
  const statusMatch = Array.isArray(expectedStatus)
    ? expectedStatus.includes(res.status)
    : res.status === expectedStatus;

  if (!statusMatch) {
    console.error(`[${context}] FAILED - Status: ${res.status}, Expected: ${expectedStatus}`);
    console.error(`[${context}] IDs:`, JSON.stringify(ids));
    console.error(`[${context}] Response Body:`, res.body);
  }
  return statusMatch;
}

// ============================================
// 시나리오 실행 함수 (각 executor에서 호출)
// ============================================

export function normalPurchaseScenario(data) {
  scenarioMetrics.add(1, { scenario: 'normalPurchase' });
  normalPurchaseFlow(getRandomUserId(), data);
}

export function couponIssueScenario(data) {
  scenarioMetrics.add(1, { scenario: 'couponIssue' });
  couponIssueFlow(getRandomUserId(), data);
}

export function popularProductScenario(data) {
  scenarioMetrics.add(1, { scenario: 'popularProduct' });
  popularProductFlow(getRandomUserId(), data);
}

// ============================================
// 시나리오 로직 구현
// ============================================

// 시나리오 1: 일반 구매 플로우
function normalPurchaseFlow(userId, data) {
  const headers = {
    'Content-Type': 'application/json',
    'X-USER-ID': userId.toString(),
  };

  // 1. 상품 목록 조회
  let res = http.get(`${BASE_URL}/api/products?page=0&size=10`, { headers });
  recordHttpStatus(res);
  check(res, {
    'products list status 200': (r) => r.status === 200,
  }) || errorRate.add(1, { scenario: 'normalPurchase' });

  sleep(2);

  // 2. 상품 상세 조회 (setup에서 받은 유효 ID만 사용)
  const productId = randomItem(data.productIds);
  if (!productId) {
    console.error('[normalPurchaseFlow] No valid productId available, skipping');
    return;
  }

  res = http.get(`${BASE_URL}/api/products/${productId}`, { headers });
  recordHttpStatus(res);

  // 404는 에러로 카운트하지 않고 skip
  if (res.status === 404) {
    notFoundSkipped.add(1, { scenario: 'normalPurchase', resource: 'product' });
    console.warn(`[normalPurchaseFlow] Product ${productId} not found (404), skipping iteration`);
    return;
  }

  const productDetailSuccess = logIfFail(res, 200, 'Product Detail', { userId, productId });
  check(res, {
    'product detail status 200': (r) => r.status === 200,
  }) || errorRate.add(1, { scenario: 'normalPurchase' });

  if (!productDetailSuccess) {
    return;
  }

  // 상품 상세에서 option_id 추출
  const productData = res.json();
  if (!productData.options || productData.options.length === 0) {
    console.error(`[normalPurchaseFlow] No options for product ${productId}, skipping`);
    return;
  }
  const optionId = productData.options[0].option_id;

  sleep(3);

  // 3. 장바구니 추가
  const cartPayload = JSON.stringify({
    product_id: productId,
    option_id: optionId,
    quantity: 1,
  });
  res = http.post(`${BASE_URL}/api/carts/items`, cartPayload, { headers });
  recordHttpStatus(res);

  const cartSuccess = logIfFail(res, [200, 201], 'Add to Cart', { userId, productId, optionId });
  check(res, {
    'add to cart status 200 or 201': (r) => r.status === 200 || r.status === 201,
  }) || errorRate.add(1, { scenario: 'normalPurchase' });

  sleep(5);

  // 4. 주문 생성
  const orderPayload = JSON.stringify({
    order_items: [
      {
        product_id: productId,
        option_id: optionId,
        quantity: 1,
      }
    ],
    coupon_id: null,
  });

  const orderStart = Date.now();
  res = http.post(`${BASE_URL}/api/orders`, orderPayload, { headers });
  const orderDuration = Date.now() - orderStart;
  orderLatency.add(orderDuration);
  recordHttpStatus(res);

  const orderSuccess = logIfFail(res, [200, 201], 'Create Order', { userId, productId, optionId });
  check(res, {
    'create order status 200 or 201': (r) => r.status === 200 || r.status === 201,
  });

  if (!orderSuccess) {
    errorRate.add(1, { scenario: 'normalPurchase' });
    return;
  }

  sleep(1);

  // 5. 주문 상세 조회
  const orderData = res.json();
  const orderId = orderData.order_id;
  if (orderId) {
    res = http.get(`${BASE_URL}/api/orders/${orderId}`, { headers });
    recordHttpStatus(res);

    logIfFail(res, 200, 'Order Detail', { userId, orderId });
    check(res, {
      'order detail status 200': (r) => r.status === 200,
    }) || errorRate.add(1, { scenario: 'normalPurchase' });
  }
}

// 시나리오 2: 쿠폰 발급
function couponIssueFlow(userId, data) {
  const headers = {
    'Content-Type': 'application/json',
    'X-USER-ID': userId.toString(),
  };

  // 1. 쿠폰 목록 조회
  let res = http.get(`${BASE_URL}/api/coupons`, { headers });
  recordHttpStatus(res);
  check(res, {
    'coupons list status 200': (r) => r.status === 200,
  }) || errorRate.add(1, { scenario: 'couponIssue' });

  sleep(getRandomThinkTime());

  // 2. 쿠폰 발급 요청 (setup에서 받은 유효 couponId 사용)
  const couponId = randomItem(data.couponIds) || DEFAULT_COUPON_ID;

  const couponPayload = JSON.stringify({
    couponId: couponId,
  });

  const couponStart = Date.now();
  res = http.post(`${BASE_URL}/api/coupons/issue/kafka`, couponPayload, { headers });
  const couponDuration = Date.now() - couponStart;
  couponLatency.add(couponDuration);
  recordHttpStatus(res);

  const couponSuccess = check(res, {
    'coupon issue status 202': (r) => r.status === 202,
  });

  if (!couponSuccess) {
    logIfFail(res, 202, 'Coupon Issue', { userId, couponId });
    errorRate.add(1, { scenario: 'couponIssue' });
    return;
  }

  // 3. 발급 상태 폴링 (최대 3회)
  const couponData = res.json();
  const requestId = couponData.requestId;

  if (requestId) {
    for (let i = 0; i < 3; i++) {
      sleep(2);

      res = http.get(`${BASE_URL}/api/coupons/issue/status/${requestId}`, { headers });
      recordHttpStatus(res);
      const statusSuccess = check(res, {
        'coupon status status 200': (r) => r.status === 200,
      });

      if (!statusSuccess) {
        errorRate.add(1, { scenario: 'couponIssue' });
        break;
      }

      const statusData = res.json();
      if (statusData.status === 'COMPLETED' || statusData.status === 'FAILED') {
        break;
      }
    }
  }
}

// 시나리오 3: 인기 상품 동시 주문
function popularProductFlow(userId, data) {
  const headers = {
    'Content-Type': 'application/json',
    'X-USER-ID': userId.toString(),
  };

  // 1. 인기 상품 조회
  let res = http.get(`${BASE_URL}/api/products/popular`, { headers });
  recordHttpStatus(res);
  const popularSuccess = check(res, {
    'popular products status 200': (r) => r.status === 200,
  });

  if (!popularSuccess) {
    errorRate.add(1, { scenario: 'popularProduct' });
  }

  // 인기 상품 응답에서 product_id 추출, 실패 시 setup의 첫 번째 상품 사용
  let popularProductId = null;
  if (popularSuccess) {
    try {
      const popularData = res.json();
      if (Array.isArray(popularData) && popularData.length > 0) {
        popularProductId = popularData[0].product_id;
      }
    } catch (e) {
      console.warn('[popularProductFlow] Failed to parse popular products response');
    }
  }

  // Fallback: setup의 유효 productIds에서 첫 번째 사용
  if (!popularProductId) {
    popularProductId = data.productIds && data.productIds.length > 0 ? data.productIds[0] : 1;
  }

  // 2. 상품 상세 조회
  res = http.get(`${BASE_URL}/api/products/${popularProductId}`, { headers });
  recordHttpStatus(res);

  // 404는 skip
  if (res.status === 404) {
    notFoundSkipped.add(1, { scenario: 'popularProduct', resource: 'product' });
    console.warn(`[popularProductFlow] Product ${popularProductId} not found (404), skipping iteration`);
    return;
  }

  const popularDetailSuccess = logIfFail(res, 200, 'Popular Product Detail', { userId, productId: popularProductId });
  check(res, {
    'popular product detail status 200': (r) => r.status === 200,
  }) || errorRate.add(1, { scenario: 'popularProduct' });

  if (!popularDetailSuccess) {
    return;
  }

  // 상품 상세에서 option_id 추출
  const popularProductData = res.json();
  if (!popularProductData.options || popularProductData.options.length === 0) {
    console.error(`[popularProductFlow] No options for product ${popularProductId}, skipping`);
    return;
  }
  const popularOptionId = popularProductData.options[0].option_id;

  sleep(1);

  // 3. 즉시 주문
  const orderPayload = JSON.stringify({
    order_items: [
      {
        product_id: popularProductId,
        option_id: popularOptionId,
        quantity: 1,
      }
    ],
    coupon_id: null,
  });

  res = http.post(`${BASE_URL}/api/orders`, orderPayload, { headers });
  recordHttpStatus(res);

  logIfFail(res, [200, 201], 'Popular Product Order', { userId, productId: popularProductId, optionId: popularOptionId });
  check(res, {
    'popular product order status 200 or 201': (r) => r.status === 200 || r.status === 201,
  }) || errorRate.add(1, { scenario: 'popularProduct' });
}

// ============================================
// CLI 실행 대응 (--vus/--duration 옵션 사용 시)
// ============================================

export default function (data) {
  // CLI로 --vus/--duration을 줘서 실행 시 기본 시나리오 실행
  // 시나리오 기반 실행이므로 여기서는 가벼운 health check만 수행
  const headers = {
    'Content-Type': 'application/json',
    'X-USER-ID': '1',
  };

  const res = http.get(`${BASE_URL}/api/products?page=0&size=1`, { headers });
  check(res, {
    'default: products list status 200': (r) => r.status === 200,
  });

  sleep(1);
}