/**
 * Peak Test (PT-001) - 선착순 쿠폰 발급 급증 트래픽
 *
 * ⚙️ 시딩 범위 및 유효 ID 풀 사용:
 * - users: 1~1000 (USER_ID_MIN ~ USER_ID_MAX)
 * - coupons: 1~2 (setup()에서 실제 유효 ID 풀 생성)
 * - 404 에러 방지: setup()에서 가져온 유효 couponId만 사용
 *
 * 테스트 목표:
 * - 이벤트 시작 시 트래픽 급증 시나리오 검증
 * - Kafka Consumer 처리 성능 검증
 *
 * 테스트 구성:
 * - 준비: 1000 VUs, 30초 (쿠폰 목록 조회 대기)
 * - 급증: 5000 VUs, 5초 (쿠폰 발급 요청 폭증)
 * - 폴링: 2000 VUs, 55초 (상태 조회 폴링)
 * - 정리: 500 VUs, 60초 (나머지 요청 처리)
 *
 * 검증 목표:
 * - Kafka 메시지 유실 0건
 * - Consumer Lag < 5000 (외부 모니터링)
 * - 중복 발급 0건
 * - 에러율 < 3%
 *
 * 성공 기준:
 * - 쿠폰 발급 성공률 > 97%
 * - P95 응답 시간 < 200ms (발급 API)
 * - Consumer Lag 5분 내 해소 (외부 모니터링)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import exec from 'k6/execution';

// ============================================
// 환경 변수 및 상수 정의
// ============================================

const BASE_URL = __ENV.BASE_URL || 'http://app:8080';

// 시딩 데이터 범위 (고정)
const USER_ID_MIN = 1;
const USER_ID_MAX = 1000;
const DEFAULT_COUPON_ID = 1;

// ============================================
// 커스텀 메트릭
// ============================================

const errorRate = new Rate('errors');
const couponIssueLatency = new Trend('coupon_issue_latency');
const couponIssueSuccess = new Rate('coupon_issue_success');
const duplicateIssue = new Counter('duplicate_issue');
const totalRequests = new Counter('total_requests');

// ============================================
// k6 설정
// ============================================

export const options = {
  stages: [
    { duration: '30s', target: 1000 },  // 준비: 1000 VUs (대기)
    { duration: '5s', target: 5000 },   // 급증: 5000 VUs (쿠폰 발급 폭증)
    { duration: '55s', target: 2000 },  // 폴링: 2000 VUs (상태 조회)
    { duration: '60s', target: 500 },   // 정리: 500 VUs (나머지 처리)
  ],
  thresholds: {
    'errors': ['rate<0.03'],
    'coupon_issue_success': ['rate>0.97'],
    'coupon_issue_latency': ['p(95)<200', 'p(99)<500'],
    'duplicate_issue': ['count==0'],
  },
};

// ============================================
// Setup: 유효 ID 풀 생성 (테스트 시작 시 1회 실행)
// ============================================

export function setup() {
  console.log('🔧 Setup: 유효 couponId 풀 생성 중...');

  const headers = {
    'Content-Type': 'application/json',
    'X-USER-ID': '1',
  };

  // 유효 couponIds 수집
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

  console.log(`✅ Setup 완료: coupons=${couponIds.length}개`);

  return {
    couponIds: couponIds,
  };
}

// ============================================
// Helper 함수
// ============================================

// VU별 requestId 저장소 (per-VU memory)
let vuRequestIds = [];

// 배열에서 랜덤 아이템 선택
function randomItem(arr) {
  if (!arr || arr.length === 0) return null;
  return arr[Math.floor(Math.random() * arr.length)];
}

// 랜덤 userId 생성
function getRandomUserId() {
  return Math.floor(Math.random() * (USER_ID_MAX - USER_ID_MIN + 1)) + USER_ID_MIN;
}

// 실제 경과 시간 기반 단계 판별
function getCurrentStage() {
  const elapsedMs = Date.now() - exec.scenario.startTime;

  const PREPARE_END = 30000;
  const SPIKE_END = 35000;
  const POLLING_END = 90000;

  if (elapsedMs < PREPARE_END) {
    return 'prepare';
  } else if (elapsedMs < SPIKE_END) {
    return 'spike';
  } else if (elapsedMs < POLLING_END) {
    return 'polling';
  } else {
    return 'cleanup';
  }
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
// Stage 함수들
// ============================================

// 준비 단계: 쿠폰 목록 조회 및 대기
function prepareStage(userId) {
  const headers = {
    'Content-Type': 'application/json',
    'X-USER-ID': userId.toString(),
  };

  const res = http.get(`${BASE_URL}/api/coupons`, { headers });
  totalRequests.add(1);
  check(res, {
    'coupons list status 200': (r) => r.status === 200,
  }) || errorRate.add(1);

  sleep(1);
}

// 급증 단계: 쿠폰 발급 요청 폭증
function spikeStage(userId, data) {
  const headers = {
    'Content-Type': 'application/json',
    'X-USER-ID': userId.toString(),
  };

  // setup에서 받은 유효 couponId 사용
  const couponId = randomItem(data.couponIds) || DEFAULT_COUPON_ID;

  const couponPayload = JSON.stringify({
    couponId: couponId,
  });

  const issueStart = Date.now();
  const res = http.post(`${BASE_URL}/api/coupons/issue/kafka`, couponPayload, { headers });
  totalRequests.add(1);
  const issueDuration = Date.now() - issueStart;
  couponIssueLatency.add(issueDuration);

  const issueSuccess = check(res, {
    'coupon issue status 202': (r) => r.status === 202,
    'coupon issue has requestId': (r) => {
      try {
        const data = r.json();
        return data.requestId !== undefined && data.requestId !== null;
      } catch (e) {
        return false;
      }
    },
  });

  if (issueSuccess) {
    couponIssueSuccess.add(1);

    // requestId 저장 (폴링용)
    try {
      const resData = res.json();
      if (resData.requestId) {
        vuRequestIds.push(resData.requestId);
      }
    } catch (e) {
      // JSON 파싱 실패
    }
  } else {
    couponIssueSuccess.add(0);
    errorRate.add(1);
    logIfFail(res, 202, 'Coupon Issue Spike', { userId, couponId });

    // 중복 발급 체크
    if (res.status === 400 || res.status === 409) {
      try {
        const errorData = res.json();
        if (errorData.error_message && errorData.error_message.includes('이미 발급')) {
          duplicateIssue.add(1);
        }
      } catch (e) {
        // JSON 파싱 실패 무시
      }
    }
  }

  sleep(0.1);
}

// 폴링 단계: 발급 상태 조회
function pollingStage(userId) {
  if (vuRequestIds.length === 0) {
    sleep(2);
    return;
  }

  const headers = {
    'Content-Type': 'application/json',
    'X-USER-ID': userId.toString(),
  };

  // 저장된 모든 requestId에 대해 순차 폴링
  for (let i = 0; i < vuRequestIds.length; i++) {
    const requestId = vuRequestIds[i];

    for (let attempt = 0; attempt < 3; attempt++) {
      const res = http.get(`${BASE_URL}/api/coupons/issue/status/${requestId}`, { headers });
      totalRequests.add(1);

      const statusSuccess = check(res, {
        'coupon status status 200': (r) => r.status === 200,
      });

      if (!statusSuccess) {
        errorRate.add(1);
        logIfFail(res, 200, 'Coupon Status Poll', { userId, requestId });
        break;
      }

      try {
        const statusData = res.json();

        // 중복 발급 체크
        if (statusData.status === 'FAILED' && statusData.message) {
          if (statusData.message.includes('이미 발급')) {
            duplicateIssue.add(1);
          }
        }

        // 완료 또는 실패 시 폴링 종료
        if (statusData.status === 'COMPLETED' || statusData.status === 'FAILED') {
          break;
        }
      } catch (e) {
        break;
      }

      sleep(2);
    }
  }

  sleep(1);
}

// 정리 단계: 나머지 요청 처리
function cleanupStage(userId) {
  const headers = {
    'Content-Type': 'application/json',
    'X-USER-ID': userId.toString(),
  };

  const res = http.get(`${BASE_URL}/api/coupons/issued`, { headers });
  totalRequests.add(1);
  check(res, {
    'issued coupons status 200': (r) => r.status === 200,
  }) || errorRate.add(1);

  sleep(1);
}

// ============================================
// 메인 테스트 함수
// ============================================

export default function (data) {
  const userId = getRandomUserId();
  const stage = getCurrentStage();

  switch (stage) {
    case 'prepare':
      prepareStage(userId);
      break;

    case 'spike':
      spikeStage(userId, data);
      break;

    case 'polling':
      pollingStage(userId);
      break;

    case 'cleanup':
      cleanupStage(userId);
      break;
  }
}

// ============================================
// 테스트 종료 후 요약
// ============================================

export function handleSummary(data) {
  const totalReqs = data.metrics.total_requests?.values?.count || 0;
  const errors = data.metrics.errors?.values?.rate || 0;
  const duplicates = data.metrics.duplicate_issue?.values?.count || 0;
  const couponSuccessRate = data.metrics.coupon_issue_success?.values?.rate || 0;
  const p95Latency = data.metrics.coupon_issue_latency?.values?.['p(95)'] || 0;
  const p99Latency = data.metrics.coupon_issue_latency?.values?.['p(99)'] || 0;

  const summary = {
    '========================================': '',
    'Peak Test (PT-001) 결과 요약': '',
    '========================================\n': '',
    '총 요청 수': totalReqs,
    '에러율': `${(errors * 100).toFixed(2)}% (목표: < 3%)`,
    '중복 발급': `${duplicates}건 (목표: 0건)`,
    '쿠폰 발급 성공률': `${(couponSuccessRate * 100).toFixed(2)}% (목표: > 97%)`,
    'P95 응답 시간': `${p95Latency.toFixed(2)}ms (목표: < 200ms)`,
    'P99 응답 시간': `${p99Latency.toFixed(2)}ms (목표: < 500ms)`,
    '\n========================================': '',
  };

  const passed = errors < 0.03 && duplicates === 0 && couponSuccessRate > 0.97 && p95Latency < 200;
  summary['테스트 결과'] = passed ? 'PASS ✅' : 'FAIL ❌';
  summary['========================================\n\n'] = '';

  let output = '';
  for (const [key, value] of Object.entries(summary)) {
    if (value === '') {
      output += `${key}\n`;
    } else {
      output += `${key}: ${value}\n`;
    }
  }

  return {
    'stdout': output,
  };
}