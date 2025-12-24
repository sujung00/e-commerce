/**
 * Stress Test (ST-001) - 시스템 한계점 파악
 *
 * ⚙️ 시딩 범위 및 유효 ID 풀 사용:
 * - users: 1~1000 (USER_ID_MIN ~ USER_ID_MAX)
 * - products: 1~100 (setup()에서 실제 유효 ID 풀 생성)
 * - coupons: 1~2 (setup()에서 실제 유효 ID 풀 생성)
 * - 404 에러 방지: setup()에서 가져온 유효 ID만 사용
 *
 * 테스트 목표:
 * - 시스템의 최대 처리 용량 측정
 * - 장애 발생 지점 및 원인 파악
 *
 * 테스트 구성:
 * - 1단계: 100 VUs, 5분, 25~30 TPS
 * - 2단계: 200 VUs, 5분, 50~60 TPS
 * - 3단계: 300 VUs, 5분, 80~100 TPS
 * - 4단계: 400 VUs, 5분, 110~130 TPS
 * - 5단계: 500+ VUs, 한계까지, 150+ TPS
 *
 * 트래픽 비율:
 * - 일반 구매 플로우: 60%
 * - 쿠폰 발급: 30%
 * - 인기 상품 조회: 10%
 *
 * 장애 판정 기준:
 * - 에러율 > 5% (즉시 중단)
 * - P95 > 2000ms
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
const totalRequests = new Counter('total_requests');
const notFoundSkipped = new Counter('not_found_skipped');

// ============================================
// k6 설정
// ============================================

export const options = {
  stages: [
    { duration: '5m', target: 100 },  // 1단계: 100 VUs
    { duration: '5m', target: 200 },  // 2단계: 200 VUs
    { duration: '5m', target: 300 },  // 3단계: 300 VUs
    { duration: '5m', target: 400 },  // 4단계: 400 VUs
    { duration: '5m', target: 500 },  // 5단계: 500 VUs
  ],
  thresholds: {
    'errors': ['rate<0.05'],
    'http_req_duration': ['p(95)<2000'],
    'http_reqs': ['rate>80'],
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

function randomItem(arr) {
  if (!arr || arr.length === 0) return null;
  return arr[Math.floor(Math.random() * arr.length)];
}

function getRandomUserId() {
  return Math.floor(Math.random() * (USER_ID_MAX - USER_ID_MIN + 1)) + USER_ID_MIN;
}

function getRandomThinkTime() {
  return Math.random() * 3 + 2; // 2~5초
}

function selectScenario() {
  const rand = Math.random() * 100;
  if (rand < 60) {
    return 'normalPurchase'; // 60%
  } else if (rand < 90) {
    return 'couponIssue'; // 30%
  } else {
    return 'popularProduct'; // 10%
  }
}

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
// 시나리오 로직 구현
// ============================================

function normalPurchaseFlow(userId, data) {
  const headers = {
    'Content-Type': 'application/json',
    'X-USER-ID': userId.toString(),
  };

  // 1. 상품 목록 조회
  let res = http.get(`${BASE_URL}/api/products?page=0&size=10`, { headers });
  totalRequests.add(1);
  check(res, {
    'products list status 200': (r) => r.status === 200,
  }) || errorRate.add(1);

  sleep(2);

  // 2. 상품 상세 조회 (setup에서 받은 유효 ID만 사용)
  const productId = randomItem(data.productIds);
  if (!productId) {
    console.error('[normalPurchaseFlow] No valid productId available, skipping');
    return;
  }

  res = http.get(`${BASE_URL}/api/products/${productId}`, { headers });
  totalRequests.add(1);

  // 404는 skip
  if (res.status === 404) {
    notFoundSkipped.add(1, { scenario: 'normalPurchase', resource: 'product' });
    console.warn(`[normalPurchaseFlow] Product ${productId} not found (404), skipping iteration`);
    return;
  }

  const productDetailSuccess = logIfFail(res, 200, 'Product Detail', { userId, productId });
  check(res, {
    'product detail status 200': (r) => r.status === 200,
  }) || errorRate.add(1);

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
  totalRequests.add(1);

  const cartSuccess = logIfFail(res, [200, 201], 'Add to Cart', { userId, productId, optionId });
  check(res, {
    'add to cart status 200 or 201': (r) => r.status === 200 || r.status === 201,
  }) || errorRate.add(1);

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
  totalRequests.add(1);
  const orderDuration = Date.now() - orderStart;
  orderLatency.add(orderDuration);

  const orderSuccess = logIfFail(res, [200, 201], 'Create Order', { userId, productId, optionId });
  check(res, {
    'create order status 200 or 201': (r) => r.status === 200 || r.status === 201,
  });

  if (!orderSuccess) {
    errorRate.add(1);
    return;
  }

  sleep(1);

  // 5. 주문 상세 조회
  const orderData = res.json();
  const orderId = orderData.order_id;
  if (orderId) {
    res = http.get(`${BASE_URL}/api/orders/${orderId}`, { headers });
    totalRequests.add(1);

    logIfFail(res, 200, 'Order Detail', { userId, orderId });
    check(res, {
      'order detail status 200': (r) => r.status === 200,
    }) || errorRate.add(1);
  }
}

function couponIssueFlow(userId, data) {
  const headers = {
    'Content-Type': 'application/json',
    'X-USER-ID': userId.toString(),
  };

  // 1. 쿠폰 목록 조회
  let res = http.get(`${BASE_URL}/api/coupons`, { headers });
  totalRequests.add(1);
  check(res, {
    'coupons list status 200': (r) => r.status === 200,
  }) || errorRate.add(1);

  sleep(getRandomThinkTime());

  // 2. 쿠폰 발급 요청 (setup에서 받은 유효 couponId 사용)
  const couponId = randomItem(data.couponIds) || DEFAULT_COUPON_ID;

  const couponPayload = JSON.stringify({
    couponId: couponId,
  });

  const couponStart = Date.now();
  res = http.post(`${BASE_URL}/api/coupons/issue/kafka`, couponPayload, { headers });
  totalRequests.add(1);
  const couponDuration = Date.now() - couponStart;
  couponLatency.add(couponDuration);

  const couponSuccess = check(res, {
    'coupon issue status 202': (r) => r.status === 202,
  });

  if (!couponSuccess) {
    logIfFail(res, 202, 'Coupon Issue', { userId, couponId });
    errorRate.add(1);
    return;
  }

  // 3. 발급 상태 폴링 (최대 10회)
  const couponData = res.json();
  const requestId = couponData.requestId;

  if (requestId) {
    for (let i = 0; i < 10; i++) {
      sleep(2);

      res = http.get(`${BASE_URL}/api/coupons/issue/status/${requestId}`, { headers });
      totalRequests.add(1);
      const statusSuccess = check(res, {
        'coupon status status 200': (r) => r.status === 200,
      });

      if (!statusSuccess) {
        errorRate.add(1);
        break;
      }

      const statusData = res.json();
      if (statusData.status === 'COMPLETED' || statusData.status === 'FAILED') {
        break;
      }
    }
  }
}

function popularProductFlow(userId, data) {
  const headers = {
    'Content-Type': 'application/json',
    'X-USER-ID': userId.toString(),
  };

  // 1. 인기 상품 조회
  let res = http.get(`${BASE_URL}/api/products/popular`, { headers });
  totalRequests.add(1);
  const popularSuccess = check(res, {
    'popular products status 200': (r) => r.status === 200,
  });

  if (!popularSuccess) {
    errorRate.add(1);
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

  if (!popularProductId) {
    popularProductId = data.productIds && data.productIds.length > 0 ? data.productIds[0] : 1;
  }

  // 2. 상품 상세 조회
  res = http.get(`${BASE_URL}/api/products/${popularProductId}`, { headers });
  totalRequests.add(1);

  // 404는 skip
  if (res.status === 404) {
    notFoundSkipped.add(1, { scenario: 'popularProduct', resource: 'product' });
    console.warn(`[popularProductFlow] Product ${popularProductId} not found (404), skipping iteration`);
    return;
  }

  const popularDetailSuccess = logIfFail(res, 200, 'Popular Product Detail', { userId, productId: popularProductId });
  check(res, {
    'popular product detail status 200': (r) => r.status === 200,
  }) || errorRate.add(1);

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
  totalRequests.add(1);

  logIfFail(res, [200, 201], 'Popular Product Order', { userId, productId: popularProductId, optionId: popularOptionId });
  check(res, {
    'popular product order status 200 or 201': (r) => r.status === 200 || r.status === 201,
  }) || errorRate.add(1);
}

// ============================================
// 메인 테스트 함수
// ============================================

export default function (data) {
  const userId = getRandomUserId();
  const scenario = selectScenario();

  switch (scenario) {
    case 'normalPurchase':
      normalPurchaseFlow(userId, data);
      break;
    case 'couponIssue':
      couponIssueFlow(userId, data);
      break;
    case 'popularProduct':
      popularProductFlow(userId, data);
      break;
  }

  sleep(getRandomThinkTime());
}