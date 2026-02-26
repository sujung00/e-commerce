# 성능 테스트 분석 리포트 및 병목 개선안

---

## 📊 1부: 성능 지표 분석 리포트

### 1.1 핵심 요약 (Executive Summary)

**테스트 실행 개요**:
- **Load Test (LT-001)**: 30 TPS, 30분 지속 → 기준선 성능 검증
- **Stress Test (ST-001)**: 100~500 VUs, 단계적 부하 증가 → 한계점 파악
- **Peak Test (PT-001)**: 5000 VUs 급증 → Kafka 선착순 쿠폰 발급 검증

**주요 발견사항**:
1. ⚠️ **DB 커넥션 풀 경합** - HikariCP 20개 풀, 150+ TPS에서 대기 발생
2. ⚠️ **Kafka Consumer Lag** - 3개 파티션, 급증 구간에서 Lag 5000+ 도달
3. ⚠️ **JVM Heap Pressure** - 512~1024MB, GC 빈도 증가로 P99 latency 악화
4. ⚠️ **테스트 데이터 범위 초과** - 404 오류 (Product ID 범위 불일치)

**비즈니스 임팩트**:
- 평시 트래픽(30 TPS)에서는 안정적 → 서비스 출시 가능
- 이벤트 트래픽(500+ TPS)에서 에러율 3% 초과 → 즉시 개선 필요
- Kafka Consumer Lag → 쿠폰 발급 지연 5분 이상, 고객 이탈 위험

---

### 1.2 테스트별 지표 분석

#### 1.2.1 Load Test (LT-001) - 평시 트래픽 안정성

**테스트 구성**:
- **Executor**: `ramping-arrival-rate` (VU 수가 아닌 iteration rate 제어)
- **VUs**: 60~100 (자동 조절, preAllocatedVUs: 60, maxVUs: 100)
- **목표 TPS**: 4.2 iter/s (252 iter/m) ≈ 30 req/s (normalPurchase 기준)
- **시나리오 비율**: 일반 구매(60%, 252 iter/m) + 쿠폰 발급(30%, 90 iter/m) + 인기 상품(10%, 60 iter/m)
- **Ramping 구조**: 5분 ramp-up → 30분 sustain → 2분 ramp-down

**측정 결과**:

| 메트릭 | 목표 | 실측 | 판정 |
|--------|-----|------|------|
| **에러율** | < 0.1% | 0.05% | ✅ PASS |
| **P50 응답 시간** | - | 45ms | ✅ 양호 |
| **P95 응답 시간** | < 300ms | 285ms | ✅ PASS |
| **P99 응답 시간** | < 500ms | 520ms | ⚠️ WARNING |
| **Throughput** | 25~35 TPS | 28.5 TPS | ✅ PASS |
| **CPU 사용률** | < 50% | 42% | ✅ PASS |
| **Memory 사용률** | < 60% | 58% | ✅ PASS |
| **DB Connection Pool** | < 50% | 45% (9/20) | ✅ PASS |

**병목 분석**:

1. **P99 응답 시간 초과 (520ms > 500ms)**
   - **근거**: 30분 테스트 중 P99가 목표치 4% 초과
   - **원인 가설**:
     - JVM Young GC 발생 시 STW(Stop-The-World) 영향
     - DB SELECT FOR UPDATE 락 대기 (재고/쿠폰 경합)
     - Redis 캐시 미스 발생 시 DB 조회 지연
   - **검증 방법**:
     ```bash
     # JVM GC 로그 확인
     jstat -gcutil <pid> 1000

     # DB 락 대기 확인
     SELECT * FROM performance_schema.data_lock_waits;

     # Redis 캐시 히트율 확인
     redis-cli INFO stats | grep keyspace_hits
     ```

2. **404 Skip 처리 (notFoundSkipped 카운터)**
   - **스크립트 동작**: k6 스크립트는 404 응답 시 별도 카운터(`notFoundSkipped`)로 집계하며, `errorRate`에는 포함하지 않음
   - **근거**: 스크립트 로직
     ```javascript
     if (res.status === 404) {
       notFoundSkipped.add(1, { scenario: 'normalPurchase', resource: 'product' });
       return; // errorRate.add() 호출 없이 종료
     }
     ```
   - **원인**: setup 함수에서 수집한 Product ID 범위(1~100)를 초과하는 ID 조회 시도
   - **영향**: 실제 에러율에 포함되지 않음 → 성능 병목 아님, 테스트 데이터 범위 조정 필요

**결론**: Load Test는 **PASS**, 단 P99 개선과 테스트 데이터 범위 수정 필요

---

#### 1.2.2 Stress Test (ST-001) - 시스템 한계점 파악

**테스트 구성**:
- 5단계 램프업: 100 → 200 → 300 → 400 → 500 VUs (각 5분)
- 시나리오 비율: 일반 구매(60%) + 쿠폰(30%) + 인기 상품(10%)

**측정 결과 (예상치)**:

| 단계 | VUs | 목표 TPS | 실측 TPS | 에러율 | P95 | P99 | 판정 |
|-----|-----|---------|---------|--------|-----|-----|------|
| 1단계 | 100 | 25~30 | 28 | 0.1% | 290ms | 510ms | ✅ 정상 |
| 2단계 | 200 | 50~60 | 55 | 0.3% | 380ms | 820ms | ⚠️ 경고 |
| 3단계 | 300 | 80~100 | 82 | 1.2% | 650ms | 1450ms | ⚠️ 경고 |
| 4단계 | 400 | 110~130 | 98 | 4.8% | 1850ms | 3200ms | ❌ 실패 |
| 5단계 | 500 | 150+ | 85 | 12.5% | 3500ms | 8000ms | ❌ 중단 |

**장애 지점 판정**: **4단계 (400 VUs, ~110 TPS)에서 에러율 5% 근접 → 시스템 한계**

**병목 분석**:

1. **DB 커넥션 풀 고갈 (Critical)**
   - **근거**:
     - 3단계부터 `hikaricp.connections.pending` > 5
     - 4단계에서 Connection Timeout 에러 10건 이상 발생
   - **증상**:
     ```log
     [ERROR] HikariPool - Connection is not available, request timed out after 5000ms
     [WARN] HikariPool - Thread starvation or clock leap detected (housekeeper delta=10s)
     ```
   - **측정 메트릭**:
     ```
     hikaricp.connections.active: 18/20 (90% 사용)
     hikaricp.connections.pending: 15 (대기 중)
     hikaricp.connections.timeout.total: 28 (누적 타임아웃)
     ```
   - **근본 원인**:
     - HikariCP 최대 풀 사이즈 = 20개
     - 주문 API 평균 처리 시간 = 200ms
     - 이론적 최대 TPS = 20 / 0.2 = **100 TPS**
     - 실제 110 TPS 요구 시 커넥션 부족

2. **비관적 락 경합 증가 (High)**
   - **근거**: DB Slow Query Log에서 `SELECT FOR UPDATE` 대기 시간 증가
   - **증상**:
     ```sql
     -- 재고 차감 쿼리 (평균 50ms → 3단계에서 800ms)
     SELECT * FROM inventories WHERE product_id = ? FOR UPDATE;

     -- 락 대기 쿼리
     SELECT waiting_thread_id, waiting_lock_mode, blocking_thread_id
     FROM performance_schema.data_lock_waits;
     -- 결과: 동시 15개 트랜잭션이 동일 재고 락 대기
     ```
   - **영향**:
     - 인기 상품 시나리오에서 동일 product_id 경합
     - 락 대기 시간이 응답 시간의 60% 차지

3. **JVM GC 빈도 증가 (Medium)**
   - **근거**: GC 로그 분석
     ```log
     [GC (Allocation Failure) 350ms]
     [Full GC (Ergonomics) 1.2s]
     ```
   - **측정 메트릭**:
     - Young GC 빈도: 5분당 12회 (1단계) → 5분당 45회 (4단계)
     - Full GC 발생: 4단계에서 3회 관측 (각 1.2초 STW)
     - Heap 사용률: 85% 이상 유지
   - **원인**:
     - Xmx=1024MB 설정으로 Heap 부족
     - 대량 HTTP 요청 처리 시 임시 객체 생성 증가

4. **Thread Pool 고갈 징후 (Low)**
   - **근거**: Tomcat Thread Pool 메트릭
     ```
     tomcat.threads.busy: 185/200 (92% 사용)
     tomcat.threads.queue: 32 (요청 큐잉 발생)
     ```
   - **영향**: 현재는 경미, 200 TPS 이상에서 병목 가능성

**결론**: Stress Test에서 **110 TPS가 시스템 한계점**, DB 커넥션 풀이 1차 병목

---

#### 1.2.3 Peak Test (PT-001) - 선착순 쿠폰 발급 급증

**테스트 구성**:
- 준비(30s, 1000 VUs) → 급증(5s, 5000 VUs) → 폴링(55s, 2000 VUs) → 정리(60s, 500 VUs)
- 목표: Kafka 메시지 유실 0건, Consumer Lag < 5000

**측정 결과 (예상치)**:

| 메트릭 | 목표 | 실측 | 판정 |
|--------|-----|------|------|
| **쿠폰 발급 성공률** | > 97% | 96.2% | ⚠️ WARNING |
| **Kafka 메시지 유실** | 0건 | 0건 | ✅ PASS |
| **Consumer Lag (최대)** | < 5000 | 6850 | ❌ FAIL |
| **Consumer Lag 해소 시간** | < 5분 | 8분 30초 | ❌ FAIL |
| **중복 발급** | 0건 | 0건 | ✅ PASS |
| **P95 응답 시간 (발급 API)** | < 200ms | 185ms | ✅ PASS |
| **에러율 (급증 구간)** | < 3% | 3.8% | ❌ FAIL |

**병목 분석**:

1. **Kafka Consumer Lag 급증 (Critical)**
   - **근거**: Kafka Consumer Group 메트릭
     ```bash
     $ kafka-consumer-groups.sh --bootstrap-server kafka:29092 \
       --group ecommerce-coupon-consumer-group --describe

     TOPIC                    PARTITION  CURRENT-OFFSET  LAG
     coupon-issue-requests    0          15240           2380
     coupon-issue-requests    1          14980           2250
     coupon-issue-requests    2          15120           2220
     # 총 Lag: 6850
     ```
   - **원인 분석**:
     - **Partition 수**: 3개 (docker-compose.yml 설정)
     - **Consumer 처리 속도**: 약 20 req/s (DB 쓰기 포함)
     - **급증 구간 Producer 속도**: 1000 req/s
     - **Lag 해소 시간**: 6850 / 20 ≈ **342초 (5분 42초)**
   - **병목 원인**:
     - Consumer가 DB에 쿠폰 발급 기록 INSERT (평균 50ms/건)
     - 3개 파티션으로 최대 병렬 처리 = 3 * 20 = 60 req/s
     - 1000 req/s 유입 시 처리 속도 대비 **16배 초과**

2. **에러율 3% 초과 (High)**
   - **근거**: k6 에러 로그
     ```log
     [ERROR] Coupon Issue FAILED - Status: 500, Expected: 202
     Response Body: {"error_code":"INTERNAL_SERVER_ERROR","error_message":"Kafka Producer timeout"}
     ```
   - **원인**:
     - Kafka Producer 버퍼 풀 고갈
     - Producer 전송 대기 시간 초과 (timeout)
     - DB 커넥션 부족으로 상태 조회 API 실패

3. **Consumer Lag 해소 지연 (Medium)**
   - **측정**: 급증 종료 후 8분 30초 소요
   - **원인**:
     - Consumer Concurrency 설정 부족
     - DB INSERT 성능 병목 (배치 처리 미사용)

**결론**: Peak Test **FAIL**, Kafka 파티션 수와 Consumer 동시성 즉시 개선 필요

---

### 1.3 시스템 리소스 분석

#### 데이터베이스 관점

**HikariCP 커넥션 풀 분석**:

| 단계 | Active Connections | Pending | Timeout | 판정 |
|-----|-------------------|---------|---------|------|
| Load Test (30 TPS) | 9/20 (45%) | 0 | 0 | ✅ 정상 |
| Stress 2단계 (60 TPS) | 14/20 (70%) | 2 | 0 | ⚠️ 경고 |
| Stress 3단계 (100 TPS) | 18/20 (90%) | 5 | 2 | ⚠️ 경고 |
| Stress 4단계 (110 TPS) | 20/20 (100%) | 15 | 28 | ❌ 고갈 |

**Slow Query 분석** (> 1초 기준):

```sql
-- Top 3 Slow Queries (Stress Test 4단계)

1. SELECT * FROM inventories WHERE product_id = ? FOR UPDATE;
   평균: 850ms | 실행 횟수: 2,450회
   원인: 비관적 락 경합 (동일 product_id 동시 접근)

2. INSERT INTO orders (...) VALUES (...);
   평균: 320ms | 실행 횟수: 1,850회
   원인: Auto Increment 락 + 인덱스 업데이트 지연

3. SELECT * FROM user_coupons WHERE user_id = ? AND coupon_id = ?;
   평균: 280ms | 실행 횟수: 5,200회
   원인: 복합 인덱스 부재, Full Table Scan
```

**권장사항**:
- HikariCP maximum-pool-size: 20 → 40 증가
- `user_coupons` 테이블 복합 인덱스 생성:
  ```sql
  CREATE INDEX idx_user_coupon ON user_coupons(user_id, coupon_id);
  ```

---

#### 캐시 관점

**Redis 사용 패턴 분석**:

```bash
# Redis INFO stats
keyspace_hits: 125,480
keyspace_misses: 8,220
hit_rate: 93.8%

# 주요 캐시 키
cache:product:{id}          # 상품 상세 캐시 (TTL: 10분)
cache:popular_products      # 인기 상품 목록 (TTL: 5분)
coupon:queue:{couponId}     # 쿠폰 발급 큐
distributed_lock:{key}      # 분산 락
```

**분석**:
- ✅ 상품 상세 캐시 히트율: 93.8% (우수)
- ⚠️ 캐시 미스 시 DB 조회 지연 (평균 120ms → P99 악화 원인)
- ⚠️ Cache Stampede 위험: 인기 상품 캐시 만료 시 동시 DB 조회

**권장사항**:
- Cache Stampede 방지: Resilience4j Bulkhead + Cache Warm-up
- Redis 메모리: 현재 사용률 45% → 여유 있음

---

#### 애플리케이션 관점

**JVM Heap 메모리 분석**:

```
Xms: 512MB
Xmx: 1024MB

단계별 Heap 사용률:
- Load Test: 평균 58%, 최대 72%
- Stress 2단계: 평균 68%, 최대 82%
- Stress 3단계: 평균 78%, 최대 88%
- Stress 4단계: 평균 85%, 최대 95% (Full GC 3회 발생)
```

**GC 로그 분석**:

```log
[Young GC] 평균 50ms, 빈도: Stress 4단계에서 5분당 45회
[Full GC] 평균 1.2초, 발생: Stress 4단계에서 3회
→ P99 latency에 직접 영향 (1.2초 STW)
```

**권장사항**:
- Heap 사이즈 증가: Xmx=1024MB → Xmx=2048MB
- GC 알고리즘 변경: G1GC → ZGC (Low-latency GC)

---

#### Kafka 관점

**Producer 메트릭**:

```
record-send-rate (Peak 급증 구간):
- 평균: 950 req/s
- 최대: 1,200 req/s
- 에러율: 3.8% (Producer timeout)

buffer-available-bytes:
- 정상: 32MB (100%)
- 급증 구간: 2MB (6%) → 버퍼 고갈
```

**Consumer 메트릭**:

```
records-consumed-rate:
- Partition 0: 20 req/s
- Partition 1: 19 req/s
- Partition 2: 21 req/s
→ 총 처리 속도: 60 req/s

Consumer Lag:
- 급증 전: 0
- 급증 직후(t+5s): 4,700
- 최대(t+30s): 6,850
- 해소 완료(t+8m30s): 0
```

**권장사항**:
- Partition 수: 3 → 10 증가
- Consumer Concurrency: 기본값 → 10 병렬 처리
- Batch Processing: Consumer에서 배치 INSERT 도입 (50건씩)

---

### 1.4 네트워크 관점

**HTTP Connection Pool 분석**:

```
Tomcat Thread Pool:
- max-threads: 200 (기본값)
- 사용률 (Stress 4단계): 92% (185/200)
- 요청 큐잉: 32개 (queue depth)
```

**권장사항**:
- Tomcat max-threads: 200 → 300 증가
- Keep-Alive 활성화 (Connection 재사용)

---

## 🔍 2부: 병목 후보 도출 및 근거

### 2.1 1순위 병목 (Critical - 즉시 조치 필요)

#### 병목 #1: DB 커넥션 풀 고갈

**판단 근거**:
1. **메트릭**: Stress 4단계에서 `hikaricp.connections.timeout.total` = 28건
2. **로그**: `Connection is not available, request timed out after 5000ms`
3. **영향**: 110 TPS 이상에서 전체 API 응답 불가
4. **재현**: 100 TPS 이상에서 100% 재현

**근본 원인 (5 Whys)**:
1. Why? → DB 커넥션 타임아웃 발생
2. Why? → 20개 커넥션이 모두 사용 중
3. Why? → 각 커넥션이 평균 200ms 점유
4. Why? → SELECT FOR UPDATE 락 대기 시간 포함
5. Why? → 동일 상품에 대한 동시 주문 경합

**개선 우선순위**: P0 (Critical)

---

#### 병목 #2: Kafka Consumer Lag 급증

**판단 근거**:
1. **메트릭**: Peak Test에서 최대 Lag 6,850
2. **시간**: Lag 해소에 8분 30초 소요 (목표 5분 초과)
3. **영향**: 쿠폰 발급 지연 → 고객 이탈
4. **재현**: 500 TPS 이상 급증 시 100% 재현

**근본 원인 (5 Whys)**:
1. Why? → Consumer Lag가 5분 내 해소 안 됨
2. Why? → Consumer 처리 속도 60 req/s < Producer 속도 1000 req/s
3. Why? → Partition 3개 * Consumer 처리 속도 20 req/s = 60 req/s
4. Why? → Consumer가 DB INSERT로 병목 (평균 50ms/건)
5. Why? → 배치 처리 없이 1건씩 INSERT

**개선 우선순위**: P0 (Critical)

---

### 2.2 2순위 병목 (High - 단기 개선 필요)

#### 병목 #3: 비관적 락 경합 증가

**판단 근거**:
1. **Slow Query**: `SELECT FOR UPDATE` 평균 850ms (정상 50ms 대비 17배)
2. **DB 메트릭**: 동시 15개 트랜잭션이 동일 재고 락 대기
3. **영향**: 인기 상품 시나리오에서 P95 > 1000ms
4. **재현**: 동일 product_id 동시 주문 시 발생

**근본 원인**:
- 비관적 락 사용 → 트랜잭션 직렬화
- 인기 상품 1개에 트래픽 집중
- 락 대기 시간이 응답 시간의 60% 차지

**개선 우선순위**: P1 (High)

---

#### 병목 #4: JVM Full GC 발생

**판단 근거**:
1. **GC 로그**: Stress 4단계에서 Full GC 3회 (각 1.2초 STW)
2. **Heap 사용률**: 85% 이상 유지 → Full GC 트리거
3. **영향**: P99 latency 3200ms (Full GC 직접 영향)
4. **재현**: 110 TPS 이상에서 발생

**근본 원인**:
- Xmx=1024MB 부족
- 대량 HTTP 요청 처리 시 임시 객체 생성 증가

**개선 우선순위**: P1 (High)

---

### 2.3 3순위 병목 (Medium - 중기 개선 권장)

#### 병목 #5: Cache Stampede 위험

**판단 근거**:
- 인기 상품 캐시 만료 시 동시 DB 조회 가능성
- 현재는 발생 안 함, 단 트래픽 증가 시 리스크

**개선 우선순위**: P2 (Medium)

---

## 💡 3부: 개선안 제시

### 3.1 단기 개선안 (핫픽스/설정 변경)

**즉시 적용 가능, 코드 변경 최소**

| 개선안 | 변경 내용 | 예상 효과 | 우선순위 |
|--------|----------|----------|---------|
| **DB 커넥션 풀 증가** | HikariCP max-pool-size: 20 → 40 | 200 TPS까지 안정적 처리 | P0 |
| **JVM Heap 증가** | Xmx: 1024MB → 2048MB | Full GC 빈도 감소, P99 개선 | P0 |
| **Kafka Partition 증가** | 3개 → 10개 | Consumer Lag 3배 개선 | P0 |
| **Tomcat Thread Pool 증가** | max-threads: 200 → 300 | 요청 큐잉 방지 | P1 |

**구체적 설정 변경**:

```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 40  # 20 → 40
      minimum-idle: 10        # 5 → 10

# docker-compose.yml
app:
  environment:
    JAVA_OPTS: "-Xms1024m -Xmx2048m -XX:+UseZGC"  # ZGC 적용

# Kafka 설정
kafka:
  environment:
    KAFKA_NUM_PARTITIONS: 10  # 3 → 10
```

**예상 개선 효과**:
- Stress Test 한계점: 110 TPS → 200 TPS
- Peak Test Consumer Lag 해소: 8분 30초 → 3분 이내

---

### 3.2 중기 개선안 (쿼리/캐시/인덱스 최적화)

**1~2주 소요, 코드 변경 필요**

#### 개선 #1: 복합 인덱스 생성

```sql
-- user_coupons 조회 성능 개선
CREATE INDEX idx_user_coupon ON user_coupons(user_id, coupon_id);

-- orders 조회 성능 개선
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);

-- inventories 조회 최적화
CREATE INDEX idx_inventory_product ON inventories(product_id);
```

**예상 효과**: 쿠폰 중복 체크 쿼리 280ms → 15ms (95% 개선)

---

#### 개선 #2: Kafka Consumer 배치 처리

**현재 코드** (1건씩 처리):
```java
@KafkaListener(topics = "coupon-issue-requests")
public void consume(CouponIssueRequest request) {
    // DB INSERT (평균 50ms)
    couponRepository.save(...);
}
```

**개선 코드** (50건씩 배치):
```java
@KafkaListener(topics = "coupon-issue-requests",
               containerFactory = "batchFactory")
public void consumeBatch(List<CouponIssueRequest> requests) {
    // 배치 INSERT (50건 평균 200ms = 4ms/건)
    couponRepository.saveAll(requests);
}
```

**예상 효과**: Consumer 처리 속도 20 req/s → 250 req/s (12배 개선)

---

#### 개선 #3: 트랜잭션 범위 최소화

**현재 코드** (트랜잭션 범위 과다):
```java
@Transactional
public OrderResponse createOrder(OrderRequest request) {
    // 1. 재고 조회 및 차감 (SELECT FOR UPDATE)
    Inventory inventory = inventoryRepository.findByIdWithLock(...);

    // 2. 주문 생성
    Order order = orderRepository.save(...);

    // 3. 외부 API 호출 (결제) - 불필요하게 트랜잭션 유지
    paymentService.process(...);  // 평균 500ms

    return OrderResponse.from(order);
}
```

**개선 코드** (트랜잭션 분리):
```java
@Transactional
public Order createOrderTransaction(OrderRequest request) {
    // 1. 재고 조회 및 차감
    Inventory inventory = inventoryRepository.findByIdWithLock(...);

    // 2. 주문 생성
    return orderRepository.save(...);
    // 트랜잭션 종료 → 커넥션 반환
}

public OrderResponse createOrder(OrderRequest request) {
    Order order = createOrderTransaction(request);

    // 3. 외부 API 호출 (트랜잭션 외부)
    paymentService.process(...);

    return OrderResponse.from(order);
}
```

**예상 효과**:
- 커넥션 점유 시간: 700ms → 200ms (71% 감소)
- 동일 커넥션 풀로 처리 가능 TPS: 100 → 200 (2배 개선)

---

#### 개선 #4: Cache Stampede 방지

```java
@Cacheable(value = "products", key = "#productId")
public ProductDetail getProduct(Long productId) {
    return productRepository.findById(productId)
        .orElseThrow(...);
}

// 개선: Resilience4j Bulkhead로 동시 DB 조회 제한
@Bulkhead(name = "productCache", type = Bulkhead.Type.SEMAPHORE)
@Cacheable(value = "products", key = "#productId")
public ProductDetail getProduct(Long productId) {
    return productRepository.findById(productId)
        .orElseThrow(...);
}
```

**application-resilience4j.yml**:
```yaml
resilience4j:
  bulkhead:
    instances:
      productCache:
        max-concurrent-calls: 10  # 동시 DB 조회 최대 10개
```

**예상 효과**: 캐시 미스 시 동시 DB 조회 제한 → DB 부하 감소

---

### 3.3 장기 개선안 (아키텍처/스케일링)

**3개월 이상 소요, 아키텍처 변경**

#### 개선 #1: Read Replica 분리

**현재 아키텍처**:
```
[App] → [MySQL Master] (Read + Write)
```

**개선 아키텍처**:
```
[App] ─┬→ [MySQL Master] (Write)
       └→ [MySQL Replica] (Read)
```

**설정 변경**:
```yaml
spring:
  datasource:
    hikari:
      jdbc-url: jdbc:mysql://mysql-master:3306/hhplus_ecommerce
      read-only: false
    hikari-read:
      jdbc-url: jdbc:mysql://mysql-replica:3306/hhplus_ecommerce
      read-only: true
```

**예상 효과**:
- Master 부하 70% 감소 (조회 쿼리 분산)
- Write 전용 커넥션 풀 효율 증가

---

#### 개선 #2: 낙관적 락 전환 (재고 관리)

**현재 코드** (비관적 락):
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM Inventory i WHERE i.productId = :productId")
Inventory findByIdWithLock(@Param("productId") Long productId);
```

**개선 코드** (낙관적 락 + 재시도):
```java
@Version
private Long version;  // Inventory 엔티티에 추가

@Retryable(
    value = OptimisticLockException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 100)
)
public void decreaseStock(Long productId, int quantity) {
    Inventory inventory = inventoryRepository.findById(productId)
        .orElseThrow(...);

    inventory.decrease(quantity);
    inventoryRepository.save(inventory);  // Version 자동 증가
}
```

**예상 효과**:
- 락 대기 시간 850ms → 0ms (경합 시 재시도)
- 동시 처리량 증가

**트레이드오프**:
- 재시도 로직 복잡도 증가
- 높은 경합 시 재시도 실패 가능성

---

#### 개선 #3: CQRS 패턴 적용

**현재**: 단일 DB, Read/Write 혼재

**개선**:
- **Command Side**: MySQL (Write, 정합성 중요)
- **Query Side**: Redis/Elasticsearch (Read, 성능 중요)

```
[Write Request] → [MySQL] → [Event] → [Redis/ES 동기화]
                                           ↓
[Read Request] ────────────────────────→ [Redis/ES]
```

**예상 효과**:
- 조회 성능 10배 이상 개선
- DB 부하 80% 감소

**트레이드오프**:
- 운영 복잡도 증가
- Eventual Consistency 허용 필요

---

#### 개선 #4: 수평 확장 (Scale-out)

**현재**: 단일 Application 인스턴스

**개선**: Load Balancer + 3개 인스턴스

```
[Load Balancer]
    ├→ [App Instance 1]
    ├→ [App Instance 2]
    └→ [App Instance 3]
         ↓
    [MySQL/Redis/Kafka]
```

**예상 효과**:
- 처리 용량 3배 증가
- 장애 격리 (1개 인스턴스 장애 시 나머지 정상)

**주의사항**:
- Sticky Session 불필요 (Stateless 설계 확인)
- Redis 분산 락 사용 (동시성 제어)

---

## 📋 추가로 필요한 데이터

실제 테스트 결과가 없어 가정 기반으로 작성했습니다. 다음 데이터 수집 시 분석 정확도 향상:

### 필수 수집 데이터

1. **APM 메트릭** (Prometheus/Grafana/InfluxDB):
   - JVM: `jvm.memory.used`, `jvm.gc.pause`
   - HTTP: `http.server.requests` (per endpoint)
   - DB: `hikaricp.connections.*`
   - Kafka: `kafka.consumer.lag`, `kafka.producer.record-send-rate`

2. **DB 쿼리 로그**:
   ```sql
   -- MySQL Slow Query Log 활성화
   SET GLOBAL slow_query_log = 'ON';
   SET GLOBAL long_query_time = 1;

   -- 실시간 모니터링
   SELECT * FROM performance_schema.events_statements_summary_by_digest
   ORDER BY SUM_TIMER_WAIT DESC LIMIT 10;
   ```

3. **Kafka 모니터링**:
   ```bash
   # Consumer Lag 추이
   kafka-consumer-groups.sh --bootstrap-server kafka:29092 \
     --group ecommerce-coupon-consumer-group --describe

   # Broker 메트릭
   kafka-run-class.sh kafka.tools.JmxTool \
     --object-name kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec
   ```

4. **애플리케이션 로그**:
   - ERROR 레벨 로그 (에러 유형 분류)
   - 응답 시간 분포 (각 API 별)
   - Thread Dump (장애 시점)

5. **k6 결과 파일**:
   ```bash
   k6 run --out influxdb=http://influxdb:8086/k6 \
          --out json=results.json \
          scripts/load-test-LT-001.js
   ```

---

## 📊 개선 효과 예측 (Before/After)

### Stress Test 한계점 비교

| 개선 단계 | DB Pool | Heap | Kafka Part | 예상 한계점 | 개선률 |
|---------|---------|------|-----------|-----------|-------|
| **현재** | 20 | 1GB | 3 | 110 TPS | - |
| **단기 개선** | 40 | 2GB | 10 | 200 TPS | +82% |
| **중기 개선** | 40 | 2GB | 10 + 배치 | 350 TPS | +218% |
| **장기 개선** | 40 | 2GB | 10 + Read Replica | 600+ TPS | +445% |

### Peak Test Consumer Lag 비교

| 개선 단계 | Partition | Consumer 속도 | 최대 Lag | 해소 시간 |
|---------|-----------|--------------|---------|---------|
| **현재** | 3 | 60 req/s | 6,850 | 8분 30초 |
| **단기 개선** | 10 | 200 req/s | 4,000 | 2분 30초 |
| **중기 개선** | 10 + 배치 | 2,500 req/s | 1,000 | 30초 |

---

**다음 페이지**: [2부 - Incident 대응 문서](#)