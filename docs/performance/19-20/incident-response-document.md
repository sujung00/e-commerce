# Incident 대응 문서

---

## 📌 Incident Summary (한 줄 요약)

**"Stress Test 110 TPS 초과 시 DB 커넥션 풀 고갈로 인한 전체 API 응답 불가 및 Peak Test에서 Kafka Consumer Lag 급증으로 쿠폰 발급 지연 8분 30초 발생"**

---

## 📅 Timeline (탐지~완화~복구)

### Phase 1: 탐지 및 초기 대응

| 시간 | 단계 | 내용 | 담당자 |
|------|------|------|--------|
| **T+0m** | 🔍 **탐지** | Grafana 대시보드 육안 확인 → Stress Test 4단계(400 VUs, 110 TPS) 진입 직후 에러율 4.8% 급증 관찰 | 성능 테스트 담당자 |
| **T+1.5m** | 📊 **Incident 판단** | `hikaricp.connections.active` 20/20 (100%) 확인, `hikaricp.connections.pending` 15건 관찰 → Incident 확정 | SRE Team |
| **T+3m** | 📞 **팀 소집** | Backend/SRE 팀 Slack 호출, Incident 논의 시작 | SRE Team |
| **T+5m** | 🔬 **원인 조사** | DB 커넥션 풀 고갈 확인, Slow Query 로그 분석 시작 | Backend Team |
| **T+7m** | 📊 **영향 파악** | 모든 API 응답 시간 > 5초 (예상), Timeout 에러 누적 28건 (예상) | SRE Team |

### Phase 2: 완화 조치

| 시간 | 단계 | 내용 | 담당자 |
|------|------|------|--------|
| **T+10m** | ⏸️ **테스트 중단** | k6 테스트 즉시 중단, 부하 제거 | SRE Team |
| **T+12m** | 🔄 **서비스 복구 확인** | 애플리케이션 Health Check: 정상, DB 커넥션 풀 정리됨 | Backend Team |
| **T+15m** | 🛡️ **임시 조치** | DB 커넥션 풀 긴급 증가 (20 → 30) 및 재배포 | DevOps Team |
| **T+25m** | ✅ **검증** | 동일 부하(110 TPS)로 재테스트 → 에러율 1.2%로 감소 | SRE Team |

### Phase 3: Peak Test Incident

| 시간 | 단계 | 내용 | 담당자 |
|------|------|------|--------|
| **T+60m** | 🔍 **탐지** | Peak Test 급증 구간(5000 VUs) 진입, Kafka Consumer Lag 급증 | k6 모니터링 |
| **T+62m** | 📊 **모니터링** | Consumer Lag: 4,700 → 6,850 (2분 만에 45% 증가) | Kafka UI |
| **T+65m** | ⚠️ **에러 발생** | 쿠폰 발급 API 에러율 3.8% (Kafka Producer timeout) | Application Log |
| **T+68m** | 📈 **Lag 추이 확인** | Lag 해소 속도: 60 req/s (예상 해소 시간: 8분 이상) | SRE Team |
| **T+76m** | ✅ **자연 복구** | Consumer Lag 0 도달, 총 해소 시간 8분 30초 | Kafka Consumer |

### Phase 4: 복구 및 사후 조치

| 시간 | 단계 | 내용 | 담당자 |
|------|------|------|--------|
| **T+90m** | 🔬 **근본 원인 분석** | DB 커넥션 풀, Kafka Partition 부족 확인 | Backend Team |
| **T+120m** | 📝 **개선 계획 수립** | 단기/중기/장기 개선안 도출 | Tech Lead |
| **T+180m** | 📊 **포스트모템 작성** | Incident 문서 작성 및 공유 | SRE Team |
| **T+1일** | 🚀 **핫픽스 배포** | DB Pool 40, Kafka Partition 10, Heap 2GB 적용 | DevOps Team |

---

## 💥 Impact (사용자/비즈니스 영향)

### 사용자 영향

**Stress Test Incident (110 TPS 초과)**:
- ❌ **영향받은 사용자**: 테스트 환경, 실사용자 영향 없음
- ⏱️ **서비스 다운타임**: 0분 (테스트 환경)
- 📊 **영향 범위**: 모든 API 응답 불가 (에러율 12.5%)
- 🔴 **심각도**: **P1** (테스트 환경이지만 프로덕션 발생 시 Critical)

**Peak Test Incident (Kafka Consumer Lag)**:
- ❌ **영향받은 사용자**: 쿠폰 발급 요청 사용자 약 5,000명 (테스트)
- ⏱️ **발급 지연 시간**: 최대 8분 30초
- 📊 **영향 범위**: 쿠폰 발급 API (발급률 96.2%, 목표 97% 미달)
- 🟡 **심각도**: **P2** (기능은 동작하나 SLA 미달)

### 비즈니스 영향 (프로덕션 발생 시 예상)

| 시나리오 | 영향 | 손실 예측 |
|---------|------|----------|
| **DB 커넥션 고갈** | 전체 주문 불가, 매출 손실 | 110 TPS × 5분 × 평균 주문액 50,000원 = **1,650만원** |
| **쿠폰 발급 지연** | 고객 신뢰도 하락, 이벤트 실패 | 이탈률 30% × 5,000명 × CLTV 300,000원 = **4.5억원** |

**가정**: 프로덕션 환경에서 동일 Incident 발생 시

---

## 🔎 Detection (알람/대시보드/징후)

### 🔴 실제 탐지 방법 (현재 시스템)

#### 1. Grafana 대시보드 수동 모니터링

**탐지 경로**: Grafana 대시보드 육안 확인 → 이상 징후 발견 → Incident 판단

**Stress Test Incident (T+0m)**:
- **모니터링 화면**: Grafana → "Application Metrics" 대시보드
- **관찰 내용**:
  - `hikaricp.connections.active`: 18/20 → 20/20 (100% 사용률 확인)
  - `hikaricp.connections.pending`: 0 → 15 (대기 큐 급증 관찰)
  - `http.server.requests.error_rate`: 0.1% → 4.8% (에러율 급증 관찰)
- **Incident 판단**: 에러율 4.8% 초과 시점에 문제 인지 (T+0m)
- **소요 시간**: 대시보드 새로고침 주기 30초 + 육안 확인 1분 = **약 1.5분**

**Peak Test Incident (T+60m)**:
- **모니터링 화면**: Grafana → "Kafka Metrics" 대시보드
- **관찰 내용**:
  - `kafka.consumer.lag`: 0 → 4,700 → 6,850 (Lag 급증 관찰)
  - `kafka.consumer.records_consumed_rate`: 60 req/s 유지 (처리 속도 정체 확인)
- **Incident 판단**: Consumer Lag 5,000 초과 시점에 문제 인지 (T+62m)
- **소요 시간**: Lag 증가 추이 관찰 후 판단 = **약 2분**

**한계점**:
- ⚠️ 실시간 알림 없음 → 탐지 지연 (1.5~2분)
- ⚠️ 대시보드 미확인 시 Incident 놓칠 위험
- ⚠️ 24시간 모니터링 불가 (운영자 부재 시간대)

---

### 🟢 권장 탐지 방법 (개선 구성 가정)

아래는 알림 시스템 구축 시 예상되는 자동 탐지 메커니즘입니다.

#### 1. Grafana Alert 설정 (가정)

**알람 규칙 예시**:
```yaml
# DB Connection Pool Alert
- alert: HikariCPConnectionTimeout
  expr: hikaricp_connections_timeout_total > 10
  for: 2m
  severity: critical
  annotations:
    summary: "DB 커넥션 풀 타임아웃 발생"
    description: "누적 타임아웃: {{ $value }}건"

# Kafka Consumer Lag Alert
- alert: KafkaConsumerLagHigh
  expr: kafka_consumer_lag > 5000
  for: 3m
  severity: warning
  annotations:
    summary: "Kafka Consumer Lag 임계치 초과"
    description: "현재 Lag: {{ $value }}"
```

**예상 알람 발송 내역** (구성 시):
- T+2m: HikariCPConnectionTimeout → PagerDuty + Slack #alerts
- T+62m: KafkaConsumerLagHigh → Slack #alerts

**예상 개선 효과**:
- ✅ 탐지 시간 1.5분 → **30초** (실시간 알림)
- ✅ 24시간 자동 모니터링 가능
- ✅ On-call 엔지니어 자동 호출 (PagerDuty)

#### 2. k6 Test Thresholds

**Stress Test 실패 판정**:
```javascript
thresholds: {
  'errors': ['rate<0.05'],  // ❌ 실제: 12.5% (FAIL)
  'http_req_duration': ['p(95)<2000'],  // ❌ 실제: 3500ms (FAIL)
}
```

**Peak Test 실패 판정**:
```javascript
thresholds: {
  'coupon_issue_success': ['rate>0.97'],  // ❌ 실제: 96.2% (FAIL)
  'kafka.consumer.lag': ['< 5000'],  // ❌ 실제: 6850 (FAIL)
}
```

#### 3. 애플리케이션 로그 기반 징후 감지

**DB 커넥션 에러 로그**:
```log
[2025-12-25 10:15:23.456] [ERROR] c.h.h.p.HikariPool - HikariPool-1 - Connection is not available, request timed out after 5000ms.
[2025-12-25 10:15:24.123] [WARN] c.h.h.p.HikariPool - HikariPool-1 - Thread starvation or clock leap detected (housekeeper delta=12s).
```

**Kafka Producer 에러 로그**:
```log
[2025-12-25 11:02:15.789] [ERROR] o.a.k.c.p.i.Sender - [Producer clientId=ecommerce-producer-1] Failed to send record to topic coupon-issue-requests: Expiring 38 record(s) for coupon-issue-requests-0:120005 ms has passed since batch creation
```

**개선 방안** (가정):
- 로그 집계 시스템 (ELK Stack) 도입
- ERROR 로그 자동 알림 → Slack 연동

---

### 수동 대시보드 관찰 패턴 (현재 운영 방식)

**대시보드 메트릭 체크리스트**:
1. **JVM Heap 사용률**: 85% 이상 → Full GC 임박
2. **HTTP 응답 시간**: P95 > 1000ms → 성능 저하
3. **Tomcat Thread Pool**: 사용률 > 90% → Thread 고갈 징후
4. **DB Connection Pool**: Active > 90% → 커넥션 부족 징후

**실제 관찰 Timeline** (Grafana 대시보드 육안 확인):
- T-5m: CPU 사용률 42% → 68% (관찰됨, 아직 정상 범위)
- T-3m: DB Active Connections 14 → 18 (증가 추이 관찰)
- T-1m: HTTP P95 latency 300ms → 850ms (2.8배 증가, 경고 수준)
- T+0m: 에러율 0.1% → 4.8% (급증 관찰) → **Incident 확정 판단**

---

## 🔍 Root Cause (근본 원인 분석)

### Primary Root Cause: DB 커넥션 풀 설계 부족

#### 5 Whys 분석

**1. Why? DB Connection Timeout이 발생했는가?**
→ HikariCP 커넥션 풀 20개가 모두 사용 중이었기 때문

**2. Why? 20개 커넥션이 모두 사용 중이었는가?**
→ 각 커넥션이 평균 200ms 이상 점유하고 있었기 때문

**3. Why? 커넥션 점유 시간이 200ms나 되었는가?**
→ SELECT FOR UPDATE 락 대기 시간(평균 850ms)이 포함되었기 때문

**4. Why? 락 대기 시간이 길었는가?**
→ 동일 상품(product_id)에 대한 동시 주문으로 비관적 락 경합 발생

**5. Why? 커넥션 풀 사이즈가 20개로 설정되었는가?**
→ **초기 설계 시 예상 트래픽(30 TPS) 기준으로 설정, 피크 트래픽(110+ TPS) 미고려**

#### 근본 원인 (Root Cause)

**"초기 용량 계획 시 평시 트래픽(30 TPS)만 고려하고 피크/이벤트 트래픽(100+ TPS)을 고려하지 않아, DB 커넥션 풀(20개)과 Kafka 파티션(3개)이 부족하게 설계됨"**

---

### Secondary Root Cause: Kafka Consumer 처리 성능 병목

#### 5 Whys 분석

**1. Why? Consumer Lag가 8분 30초 동안 지속되었는가?**
→ Consumer 처리 속도(60 req/s) < Producer 속도(1000 req/s)이기 때문

**2. Why? Consumer 처리 속도가 60 req/s에 불과했는가?**
→ Partition 3개 × 각 Consumer 처리 속도 20 req/s = 60 req/s

**3. Why? Consumer 처리 속도가 20 req/s였는가?**
→ DB INSERT 작업에 평균 50ms/건 소요되기 때문

**4. Why? DB INSERT가 느렸는가?**
→ 1건씩 INSERT 처리 (배치 처리 미사용), Auto Increment 락 경합

**5. Why? 배치 처리를 사용하지 않았는가?**
→ **초기 구현 시 단순성 우선, 대용량 트래픽 시나리오 미고려**

#### 근본 원인 (Root Cause)

**"Kafka Consumer 설계 시 평시 트래픽(10 req/s) 기준으로 개발, 선착순 이벤트(1000 req/s 급증) 시나리오를 고려하지 않아 Partition 수(3개) 및 배치 처리 미적용"**

---

### Contributing Factors (기여 요인)

| 요인 | 설명 | 영향도 |
|-----|------|--------|
| **JVM Heap 부족** | Xmx=1024MB로 Full GC 발생 → P99 latency 악화 | Medium |
| **트랜잭션 범위 과다** | 외부 API 호출까지 트랜잭션 유지 → 커넥션 점유 시간 증가 | High |
| **인덱스 부재** | `user_coupons` 복합 인덱스 없음 → 쿠폰 중복 체크 느림 | Low |
| **모니터링 부족** | Consumer Lag 알람 임계치 5000 → 너무 높음 | Low |

---

### 데이터 기반 근거

#### DB 커넥션 풀 수식

```
최대 처리 가능 TPS = Pool Size / Avg Holding Time
                   = 20 / 0.2s
                   = 100 TPS

실제 요구 TPS = 110 TPS → 초과 → 타임아웃 발생
```

#### Kafka Consumer Lag 수식

```
Lag 증가율 = Producer Rate - Consumer Rate
          = 1000 req/s - 60 req/s
          = 940 req/s

최대 Lag (급증 구간 5초) = 940 × 5 = 4,700
실제 측정 Lag = 6,850 (Producer 순간 피크로 인한 추가 증가)

Lag 해소 시간 = Max Lag / Consumer Rate
              = 6,850 / 60
              = 114초 ≈ 1분 54초

실제 해소 시간 = 8분 30초 (DB INSERT 지연, GC 영향 포함)
```

---

## 🛡️ Mitigation & Recovery (실제 대응 단계)

### Immediate Actions (즉시 조치 - T+0m ~ T+15m)

#### Step 1: 테스트 중단 및 부하 제거 (T+10m)

**실행 명령어**:
```bash
# k6 컨테이너 즉시 중지
docker stop k6

# 진행 중인 테스트 프로세스 종료 확인
docker ps | grep k6
# 결과: (없음) → 테스트 중단 확인
```

**검증**:
```bash
# 애플리케이션 CPU/Memory 정상화 확인
docker stats ecommerce-app --no-stream
# CPU: 68% → 15% (정상)
# Memory: 920MB → 580MB (정상)
```

#### Step 2: 서비스 상태 확인 (T+12m)

**Health Check**:
```bash
# Actuator Health Endpoint
curl -s http://localhost:8090/actuator/health | jq

# 응답:
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" },
    "kafka": { "status": "UP" }
  }
}
```

**DB 커넥션 풀 상태 확인**:
```bash
# Actuator Metrics
curl -s http://localhost:8090/actuator/metrics/hikaricp.connections.active | jq

# 응답:
{
  "name": "hikaricp.connections.active",
  "measurements": [
    { "statistic": "VALUE", "value": 2.0 }  # 20 → 2로 정리됨
  ]
}
```

#### Step 3: 데이터 정합성 검증 (T+14m)

**DB 검증 쿼리**:
```sql
-- 주문 테이블 정합성 확인
SELECT COUNT(*) as total_orders,
       COUNT(DISTINCT user_id) as unique_users,
       SUM(total_amount) as total_sales
FROM orders
WHERE created_at > NOW() - INTERVAL 30 MINUTE;

-- 재고 음수 확인 (있으면 안 됨)
SELECT * FROM inventories WHERE stock_quantity < 0;
-- 결과: (없음) → 정합성 정상

-- 쿠폰 중복 발급 확인
SELECT user_id, coupon_id, COUNT(*) as cnt
FROM user_coupons
WHERE coupon_id = 1
GROUP BY user_id, coupon_id
HAVING cnt > 1;
-- 결과: (없음) → 중복 발급 없음
```

**결론**: ✅ 데이터 정합성 이상 없음

---

### Short-term Fix (긴급 핫픽스 - T+15m ~ T+60m)

#### Step 1: DB 커넥션 풀 증가

**설정 변경** (`application.yml`):
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30  # 20 → 30 (50% 증가)
      minimum-idle: 8         # 5 → 8
      connection-timeout: 10000  # 5000 → 10000 (타임아웃 완화)
```

**재배포**:
```bash
# 설정 변경 후 재빌드
./gradlew clean build -x test

# 컨테이너 재시작
docker-compose restart app

# 헬스 체크
curl http://localhost:8090/actuator/health
```

#### Step 2: 검증 테스트 (T+25m)

**동일 부하로 재테스트**:
```bash
# Stress Test 4단계만 재실행 (400 VUs)
docker run --rm -i grafana/k6:latest run \
  -e BASE_URL=http://app:8080 \
  --vus 400 --duration 5m \
  /scripts/stress-test-ST-001.js

# 결과:
# - 에러율: 12.5% → 1.2% (개선 ✅)
# - P95: 3500ms → 1200ms (개선 ✅)
# - Connection Timeout: 28건 → 2건 (개선 ✅)
```

**결론**: ✅ 긴급 핫픽스로 110 TPS 안정화 달성

---

### Long-term Fix (장기 개선 - T+1일 이후)

#### 개선 #1: JVM Heap 증가

**docker-compose.yml 변경**:
```yaml
app:
  environment:
    JAVA_OPTS: "-Xms1024m -Xmx2048m -XX:+UseZGC -XX:+ZGenerational"
```

**예상 효과**: Full GC 제거, P99 latency 520ms → 350ms

---

#### 개선 #2: Kafka Partition 증가

**Partition 재구성**:
```bash
# 기존 토픽 삭제 (테스트 환경)
kafka-topics.sh --bootstrap-server kafka:29092 \
  --delete --topic coupon-issue-requests

# 새 토픽 생성 (Partition 10개)
kafka-topics.sh --bootstrap-server kafka:29092 \
  --create --topic coupon-issue-requests \
  --partitions 10 \
  --replication-factor 1
```

**docker-compose.yml 기본값 변경**:
```yaml
kafka:
  environment:
    KAFKA_NUM_PARTITIONS: 10  # 3 → 10
```

**예상 효과**: Consumer Lag 해소 시간 8분 30초 → 2분 30초

---

#### 개선 #3: Consumer 배치 처리

**KafkaListenerConfig 변경**:
```java
@Configuration
public class KafkaListenerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CouponIssueRequest>
            batchFactory(ConsumerFactory<String, CouponIssueRequest> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, CouponIssueRequest> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);  // 배치 모드 활성화
        factory.setConcurrency(10);       // 동시 Consumer 10개

        return factory;
    }
}
```

**Consumer 코드 변경**:
```java
@KafkaListener(
    topics = "coupon-issue-requests",
    containerFactory = "batchFactory"
)
public void consumeBatch(List<CouponIssueRequest> requests) {
    // 배치 INSERT (JPA saveAll)
    List<UserCoupon> coupons = requests.stream()
        .map(this::mapToUserCoupon)
        .collect(Collectors.toList());

    userCouponRepository.saveAll(coupons);  // 50건씩 배치 처리
}
```

**예상 효과**: Consumer 처리 속도 60 req/s → 500 req/s (8배 개선)

---

## 📋 Action Items (Owner/우선순위/기한)

### Critical (P0) - 즉시 조치 필요

| Action Item | Owner | 우선순위 | 기한 | 상태 |
|------------|-------|---------|------|------|
| DB 커넥션 풀 40개로 증가 | Backend Team | P0 | 2025-12-26 | ✅ 완료 |
| JVM Heap 2GB로 증가 + ZGC 적용 | DevOps Team | P0 | 2025-12-26 | ✅ 완료 |
| Kafka Partition 10개로 증가 | Backend Team | P0 | 2025-12-27 | 🔄 진행중 |
| Tomcat Thread Pool 300으로 증가 | Backend Team | P0 | 2025-12-27 | ⏳ 예정 |

### High (P1) - 2주 내 완료

| Action Item | Owner | 우선순위 | 기한 | 상태 |
|------------|-------|---------|------|------|
| Kafka Consumer 배치 처리 구현 | Backend Team | P1 | 2025-01-08 | ⏳ 예정 |
| 복합 인덱스 생성 (user_coupons) | DBA Team | P1 | 2025-01-05 | ⏳ 예정 |
| 트랜잭션 범위 최소화 (주문 API) | Backend Team | P1 | 2025-01-10 | ⏳ 예정 |
| Consumer Lag 알람 임계치 조정 (5000 → 1000) | SRE Team | P1 | 2025-12-28 | ⏳ 예정 |

### Medium (P2) - 1개월 내 완료

| Action Item | Owner | 우선순위 | 기한 | 상태 |
|------------|-------|---------|------|------|
| Cache Stampede 방지 (Resilience4j Bulkhead) | Backend Team | P2 | 2026-01-20 | ⏳ 예정 |
| DB Slow Query 자동 알람 구성 | SRE Team | P2 | 2026-01-15 | ⏳ 예정 |
| JVM GC 로그 수집 및 대시보드 구성 | SRE Team | P2 | 2026-01-25 | ⏳ 예정 |

### Long-term (P3) - 3개월 이상

| Action Item | Owner | 우선순위 | 기한 | 상태 |
|------------|-------|---------|------|------|
| Read Replica 도입 (읽기 부하 분산) | Infra Team | P3 | 2026-03-31 | ⏳ 예정 |
| 낙관적 락 전환 검토 (재고 관리) | Backend Team | P3 | 2026-04-30 | ⏳ 예정 |
| CQRS 패턴 적용 (Read/Write 분리) | Architecture Team | P3 | 2026-06-30 | ⏳ 예정 |
| 수평 확장 (Load Balancer + 3 Instances) | Infra Team | P3 | 2026-05-31 | ⏳ 예정 |

---

## 🚨 Runbook (재발 시 즉시 실행 절차)

### Runbook #1: DB 커넥션 풀 고갈 대응

#### 증상 감지

**자동 알람**:
```
🚨 HikariCPConnectionTimeout Alert

hikaricp.connections.timeout > 10
현재값: 15건
```

**수동 확인**:
```bash
# Actuator로 커넥션 풀 상태 확인
curl -s http://localhost:8090/actuator/metrics/hikaricp.connections.active | jq

# 판정 기준:
# - active > 90% (18/20 이상) → 경고
# - active = 100% (20/20) → 위험
# - timeout > 0 → 즉시 조치
```

---

#### 즉시 대응 절차 (5분 내 완료)

**Step 1: 부하 확인 및 트래픽 제한 (T+0m)**

```bash
# 1. 현재 TPS 확인
curl -s http://localhost:8090/actuator/metrics/http.server.requests | jq '.measurements[] | select(.statistic=="COUNT")'

# 2. 비정상 트래픽 확인 (DDoS/Bot 공격)
tail -f /var/log/nginx/access.log | awk '{print $1}' | sort | uniq -c | sort -rn | head -10

# 3. 긴급 트래픽 제한 (Nginx Rate Limit)
# /etc/nginx/nginx.conf
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=50r/s;
limit_req zone=api_limit burst=20 nodelay;

sudo nginx -s reload
```

**Step 2: DB 커넥션 강제 정리 (T+2m)**

```sql
-- MySQL 프로세스 리스트 확인
SHOW PROCESSLIST;

-- 장시간 대기 중인 커넥션 강제 종료
SELECT CONCAT('KILL ', id, ';') AS kill_command
FROM information_schema.processlist
WHERE time > 60  -- 60초 이상 대기
  AND command != 'Sleep';

-- 위 쿼리 결과 실행 (주의: 트랜잭션 롤백됨)
KILL 1234;
KILL 1235;
```

**Step 3: 애플리케이션 재시작 (최후의 수단, T+4m)**

```bash
# Graceful Restart
docker-compose restart app

# 재시작 후 헬스 체크
while ! curl -s http://localhost:8090/actuator/health | grep -q "UP"; do
  echo "Waiting for app to start..."
  sleep 2
done

echo "✅ App restarted successfully"
```

---

#### 근본 원인 조사 (T+10m)

```bash
# 1. DB Slow Query 확인
docker exec mysql mysql -uroot -p${DB_PASSWORD} \
  -e "SELECT * FROM mysql.slow_log ORDER BY start_time DESC LIMIT 10;"

# 2. 락 대기 확인
docker exec mysql mysql -uroot -p${DB_PASSWORD} \
  -e "SELECT * FROM performance_schema.data_lock_waits;"

# 3. 애플리케이션 로그 확인 (에러 패턴 분석)
docker logs ecommerce-app --tail 1000 | grep -i "connection timeout"
```

---

#### 임시 조치 (T+15m)

**설정 긴급 변경**:
```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 40  # 긴급 증가
      connection-timeout: 10000  # 타임아웃 완화
```

**재배포**:
```bash
./gradlew clean build -x test
docker-compose up -d app
```

---

#### 검증 및 모니터링 (T+20m)

```bash
# 1. 커넥션 풀 사용률 모니터링 (5분간)
watch -n 5 'curl -s http://localhost:8090/actuator/metrics/hikaricp.connections.active | jq'

# 2. 에러율 확인
curl -s http://localhost:8090/actuator/metrics/http.server.requests | \
  jq '.availableTags[] | select(.tag=="status") | .values[] | select(. | startswith("5"))'

# 3. Grafana 대시보드 확인
# http://localhost:3000/d/hikaricp-dashboard
```

---

### Runbook #2: Kafka Consumer Lag 급증 대응

#### 증상 감지

**자동 알람**:
```
⚠️ KafkaConsumerLagHigh Alert

kafka.consumer.lag > 1000
현재값: 3,500
Topic: coupon-issue-requests
```

**수동 확인**:
```bash
# Consumer Group Lag 확인
docker exec kafka-kraft \
  kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group ecommerce-coupon-consumer-group \
  --describe

# 결과 예시:
# TOPIC                    PARTITION  CURRENT-OFFSET  LAG
# coupon-issue-requests    0          8,450           1,200
# coupon-issue-requests    1          8,320           1,150
# coupon-issue-requests    2          8,510           1,150
# 총 Lag: 3,500
```

---

#### 즉시 대응 절차 (10분 내 완료)

**Step 1: Consumer 상태 확인 (T+0m)**

```bash
# 1. Consumer 애플리케이션 상태 확인
docker logs ecommerce-app --tail 100 | grep "KafkaConsumer"

# 2. Consumer Thread 확인
curl -s http://localhost:8090/actuator/metrics/kafka.consumer.assigned.partitions | jq

# 예상 결과: 3 (Partition 3개 모두 할당되어야 정상)
```

**Step 2: Producer 속도 제한 (긴급 조치, T+2m)**

```java
// 애플리케이션 코드에서 Producer 속도 제한
@Service
public class CouponProducerService {

    @Autowired
    private RateLimiter rateLimiter;  // Resilience4j

    public void sendCouponIssueRequest(CouponIssueRequest request) {
        // 초당 100건으로 제한
        rateLimiter.executeSupplier(() -> {
            kafkaTemplate.send("coupon-issue-requests", request);
            return null;
        });
    }
}
```

**긴급 설정** (application.yml):
```yaml
resilience4j:
  ratelimiter:
    instances:
      couponProducer:
        limit-for-period: 100  # 긴급 제한
        limit-refresh-period: 1s
```

**Step 3: Consumer Concurrency 긴급 증가 (T+5m)**

```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, CouponIssueRequest>
        kafkaListenerContainerFactory() {

    ConcurrentKafkaListenerContainerFactory<String, CouponIssueRequest> factory =
            new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConcurrency(10);  // 3 → 10 긴급 증가

    return factory;
}
```

**재배포**:
```bash
./gradlew clean build -x test
docker-compose restart app
```

---

#### Lag 해소 모니터링 (T+10m)

**자동 모니터링 스크립트**:
```bash
#!/bin/bash
# kafka-lag-monitor.sh

while true; do
  LAG=$(docker exec kafka-kraft \
    kafka-consumer-groups.sh \
    --bootstrap-server localhost:9092 \
    --group ecommerce-coupon-consumer-group \
    --describe | \
    awk '{sum += $6} END {print sum}')

  echo "[$(date '+%Y-%m-%d %H:%M:%S')] Total Lag: $LAG"

  if [ "$LAG" -lt 100 ]; then
    echo "✅ Lag resolved (< 100)"
    break
  fi

  sleep 10
done
```

**실행**:
```bash
chmod +x kafka-lag-monitor.sh
./kafka-lag-monitor.sh
```

---

#### 근본 원인 조사 (T+20m)

```bash
# 1. Consumer 처리 속도 확인
curl -s http://localhost:8090/actuator/metrics/kafka.consumer.records.consumed.rate | jq

# 2. Producer 전송 속도 확인
curl -s http://localhost:8090/actuator/metrics/kafka.producer.record.send.rate | jq

# 3. DB INSERT 성능 확인
docker exec mysql mysql -uroot -p${DB_PASSWORD} \
  -e "SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.tables WHERE TABLE_NAME = 'user_coupons';"

# 4. 애플리케이션 로그 확인
docker logs ecommerce-app --tail 500 | grep "CouponConsumer"
```

---

#### 장기 조치 (T+1일 이후)

**Partition 재구성** (주의: 데이터 손실 위험):
```bash
# 1. 새 토픽 생성 (Partition 10개)
docker exec kafka-kraft \
  kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic coupon-issue-requests-v2 \
  --partitions 10 \
  --replication-factor 1

# 2. Producer 토픽 변경
# application.yml
spring:
  kafka:
    topics:
      coupon-issue-requests: coupon-issue-requests-v2

# 3. 기존 토픽 데이터 이관 (선택)
# Kafka Connect 또는 수동 스크립트 사용
```

---

## 📚 참고 자료

### 관련 문서

1. **성능 테스트 계획서**: `/docs/performance/19-20/load-test-plan.md`
2. **성능 분석 리포트**: `/docs/performance/19-20/performance-analysis-report.md`
3. **k6 테스트 스크립트**: `/performance/k6/scripts/`

### 모니터링 대시보드

- **Grafana**: http://localhost:3000
  - HikariCP Dashboard: `/d/hikaricp-dashboard`
  - Kafka Dashboard: `/d/kafka-dashboard`
  - JVM Dashboard: `/d/jvm-dashboard`
- **Kafka UI**: http://localhost:8080
- **Actuator**: http://localhost:8090/actuator

### 알람 채널

- **Slack**: `#alerts-production`, `#alerts-performance`
- **Email**: sre-team@example.com
- **PagerDuty**: Incident 발생 시 자동 호출

---

## ✅ Lessons Learned (교훈)

### What Went Well (잘된 점)

1. ✅ **데이터 정합성 유지**: DB 커넥션 고갈에도 불구하고 데이터 손실/중복 없음
2. ✅ **테스트 환경 격리**: 프로덕션 영향 없이 사전 발견
3. ✅ **수동 탐지 성공**: Grafana 대시보드 육안 확인으로 1.5분 내 Incident 판단
4. ✅ **자동화된 테스트 판정**: k6 Threshold로 자동 실패 판정

### What Went Wrong (문제점)

1. ❌ **용량 계획 부족**: 평시 트래픽만 고려, 피크 트래픽 미고려
2. ❌ **부하 테스트 지연**: 서비스 출시 전 충분한 테스트 시간 부족
3. ❌ **알람 임계치 과다**: Consumer Lag 알람 5000 → 너무 높음
4. ❌ **Runbook 부재**: Incident 발생 시 대응 절차 문서화 안 됨

### Action for Improvement (개선 행동)

1. 📝 **용량 계획 프로세스 수립**: 평시 × 3배 트래픽 기준 설계
2. 🧪 **정기 부하 테스트**: 월 1회 Production-like 환경 테스트
3. 📊 **알람 재조정**: Consumer Lag 1000, DB Connection 70% 등
4. 📖 **Runbook 작성**: 모든 Critical 시스템에 대응 절차 문서화

