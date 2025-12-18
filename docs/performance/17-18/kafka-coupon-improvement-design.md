# Kafka 기반 선착순 쿠폰 발급 시스템 개선 설계

## 1. 개요

### 1.1 배경

기존 Redis Queue 기반 선착순 쿠폰 발급 시스템은 중소 규모 트래픽(~1,000 req/s)에서는 안정적으로 동작하였으나, 다음과 같은 한계에 직면하였습니다:

- **확장성 한계**: Redis 단일 인스턴스 처리량 한계로 인한 병목
- **장애 복구 취약성**: Redis 장애 시 큐 데이터 손실 위험
- **운영 복잡도**: Redis Sentinel 구성 및 관리 오버헤드
- **처리량 한계**: 피크 트래픽(10,000 req/s) 대응 불가

### 1.2 목적

Kafka 기반 메시징 시스템으로 전환하여 다음을 달성합니다:

- **대규모 트래픽 대응**: 피크 10,000 req/s 안정적 처리
- **수평 확장 가능**: Consumer 증설로 처리량 선형 증가
- **정확한 선착순 보장**: Kafka Partition 내 메시지 순서 보장
- **중복 발급 방지**: DB UNIQUE 제약 + at-least-once 전략
- **장애 복구**: Offset 기반 재처리로 메시지 손실 방지

### 1.3 설계 범위

- Kafka Topic 및 Partition 설계
- Producer/Consumer 아키텍처
- 동시성 제어 및 멱등성 보장 전략
- 장애 시나리오 대응 방안
- 수평 확장 전략

---

## 2. 비즈니스 시퀀스 다이어그램

### 2.1 정상 플로우: 쿠폰 발급 성공

```
┌──────┐   ┌────────────┐   ┌─────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────┐
│Client│   │Controller  │   │Kafka        │   │Kafka         │   │Consumer      │   │Database  │
│      │   │            │   │Producer     │   │Broker        │   │              │   │          │
└──┬───┘   └─────┬──────┘   └──────┬──────┘   └──────┬───────┘   └──────┬───────┘   └────┬─────┘
   │             │                  │                 │                  │                │
   │ POST /issue │                  │                 │                  │                │
   │   /kafka    │                  │                 │                  │                │
   ├────────────>│                  │                 │                  │                │
   │             │                  │                 │                  │                │
   │             │ Redis GET        │                 │                  │                │
   │             │ (쿠폰 잔여 확인) │                 │                  │                │
   │             ├─────────────────>│                 │                  │                │
   │             │<─────────────────┤                 │                  │                │
   │             │ OK (재고 있음)   │                 │                  │                │
   │             │                  │                 │                  │                │
   │             │ send(topic,      │                 │                  │                │
   │             │  key=userId,     │                 │                  │                │
   │             │  value=request)  │                 │                  │                │
   │             ├─────────────────>│                 │                  │                │
   │             │                  │                 │                  │                │
   │             │                  │ append to       │                  │                │
   │             │                  │ partition       │                  │                │
   │             │                  ├────────────────>│                  │                │
   │             │                  │ (based on       │                  │                │
   │             │                  │  hash(userId))  │                  │                │
   │             │                  │                 │                  │                │
   │             │                  │<────────────────┤                  │                │
   │             │                  │ ack (partition, │                  │                │
   │             │                  │      offset)    │                  │                │
   │             │<─────────────────┤                 │                  │                │
   │             │ CompletableFuture│                 │                  │                │
   │             │ success callback │                 │                  │                │
   │             │                  │                 │                  │                │
   │ 202 ACCEPTED│                  │                 │                  │                │
   │ {requestId, │                  │                 │                  │                │
   │  status:    │                  │                 │                  │                │
   │  PENDING}   │                  │                 │                  │                │
   │<────────────┤                  │                 │                  │                │
   │             │                  │                 │                  │                │
   │             │                  │                 │ poll()           │                │
   │             │                  │                 │<─────────────────┤                │
   │             │                  │                 │ CouponIssueReq   │                │
   │             │                  │                 ├─────────────────>│                │
   │             │                  │                 │                  │                │
   │             │                  │                 │                  │ SELECT FOR     │
   │             │                  │                 │                  │ UPDATE (비관적락)│
   │             │                  │                 │                  ├───────────────>│
   │             │                  │                 │                  │ Lock acquired  │
   │             │                  │                 │                  │<───────────────┤
   │             │                  │                 │                  │                │
   │             │                  │                 │                  │ INSERT         │
   │             │                  │                 │                  │ user_coupon    │
   │             │                  │                 │                  ├───────────────>│
   │             │                  │                 │                  │ Success        │
   │             │                  │                 │                  │<───────────────┤
   │             │                  │                 │                  │                │
   │             │                  │                 │                  │ UPDATE coupon  │
   │             │                  │                 │                  │ issued_qty++   │
   │             │                  │                 │                  ├───────────────>│
   │             │                  │                 │                  │ COMMIT         │
   │             │                  │                 │                  │<───────────────┤
   │             │                  │                 │                  │                │
   │             │                  │                 │ ack.acknowledge()│                │
   │             │                  │                 │<─────────────────┤                │
   │             │                  │                 │ (Offset commit)  │                │
   │             │                  │                 │                  │                │
```

**핵심 포인트**:
- Controller는 Kafka 발행 후 즉시 `202 ACCEPTED` 응답 (비동기)
- Consumer는 DB 트랜잭션 성공 후에만 Offset 커밋 (at-least-once)
- 비관적 락으로 재고 차감 정확성 보장

---

### 2.2 실패 플로우 1: 쿠폰 소진

```
┌──────┐   ┌────────────┐   ┌──────────────┐   ┌──────────┐
│Client│   │Consumer    │   │Database      │   │Kafka     │
└──┬───┘   └─────┬──────┘   └──────┬───────┘   └────┬─────┘
   │             │                  │                │
   │             │ poll()           │                │
   │             │<─────────────────────────────────┤
   │             │ CouponIssueReq   │                │
   │             │                  │                │
   │             │ SELECT FOR UPDATE│                │
   │             ├─────────────────>│                │
   │             │ Lock acquired    │                │
   │             │<─────────────────┤                │
   │             │                  │                │
   │             │ Check:           │                │
   │             │ issued_qty >=    │                │
   │             │ total_qty        │                │
   │             ├─────────────────>│                │
   │             │                  │                │
   │             │ ❌ IllegalArg    │                │
   │             │ Exception:       │                │
   │             │ "쿠폰이 모두     │                │
   │             │  소진되었습니다" │                │
   │             │<─────────────────┤                │
   │             │                  │                │
   │             │ catch: Business  │                │
   │             │ Error (재시도    │                │
   │             │ 불필요)          │                │
   │             │                  │                │
   │             │ ack.acknowledge()│                │
   │             ├─────────────────────────────────>│
   │             │ (Offset commit)  │                │
   │             │ ✅ Skip message  │                │
```

**핵심 포인트**:
- 비즈니스 예외(쿠폰 소진, 중복 발급)는 재시도해도 성공할 수 없음
- **Offset을 커밋하여 메시지를 skip** (무한 재시도 방지)
- 로그에 경고 레벨로 기록하여 운영팀에서 모니터링

---

### 2.3 실패 플로우 2: 중복 발급 방지

```
┌──────┐   ┌────────────┐   ┌──────────┐
│Client│   │Consumer    │   │Database  │
└──┬───┘   └─────┬──────┘   └────┬─────┘
   │             │                │
   │             │ poll()         │
   │             │ (재처리 시나리오:│
   │             │  이전 처리 성공 │
   │             │  했으나 Offset  │
   │             │  커밋 전 장애)  │
   │             │                │
   │             │ SELECT FOR     │
   │             │ UPDATE         │
   │             ├───────────────>│
   │             │                │
   │             │ INSERT         │
   │             │ user_coupon    │
   │             ├───────────────>│
   │             │                │
   │             │ ❌ UNIQUE      │
   │             │ constraint     │
   │             │ violation:     │
   │             │ (user_id,      │
   │             │  coupon_id)    │
   │             │<───────────────┤
   │             │                │
   │             │ catch: IllegalArg│
   │             │ Exception      │
   │             │ "이미 발급받은  │
   │             │  쿠폰입니다"   │
   │             │                │
   │             │ ack.acknowledge()│
   │             │ ✅ Skip (멱등성)│
```

**핵심 포인트**:
- DB UNIQUE 제약 `(user_id, coupon_id)`로 중복 발급 차단
- at-least-once 보장으로 인한 재처리 시나리오 대응
- 멱등성 보장: 같은 메시지를 여러 번 처리해도 결과 동일

---

### 2.4 실패 플로우 3: Consumer 장애 및 재처리

```
┌──────────────┐   ┌──────────┐   ┌──────────────┐
│Consumer      │   │Database  │   │Kafka Broker  │
└──────┬───────┘   └────┬─────┘   └──────┬───────┘
       │                │                │
       │ poll()         │                │
       │<───────────────────────────────┤
       │ offset=100     │                │
       │                │                │
       │ DB 트랜잭션    │                │
       │ 처리 중...     │                │
       ├───────────────>│                │
       │                │                │
       │ ❌ DB 연결     │                │
       │ timeout!       │                │
       │<───────────────┤                │
       │                │                │
       │ catch: Exception│               │
       │ (재시도 필요)  │                │
       │                │                │
       │ ❌ Offset      │                │
       │ 커밋하지 않음  │                │
       │ (재처리 보장)  │                │
       │                │                │
       │ Consumer 재시작│                │
       │ (또는 다음 poll)│               │
       │                │                │
       │ poll()         │                │
       │<───────────────────────────────┤
       │ offset=100     │                │
       │ ✅ 같은 메시지 │                │
       │ 재처리         │                │
       │                │                │
       │ SELECT FOR UPDATE│              │
       ├───────────────>│                │
       │ Success        │                │
       │<───────────────┤                │
       │                │                │
       │ ack.acknowledge()│              │
       ├───────────────────────────────>│
       │ offset=101     │                │
```

**핵심 포인트**:
- DB 오류, 네트워크 장애 등 **일시적 오류는 재처리**
- Offset을 커밋하지 않으면 다음 poll()에서 같은 메시지 재수신
- Consumer 재시작 후에도 미처리 메시지 재처리 보장

---

## 3. Kafka 구성 설계

### 3.1 Topic 설계

| 항목 | 값 | 설명 |
|------|-----|------|
| **Topic 이름** | `coupon.issue.requests` | 쿠폰 발급 요청 토픽 |
| **Partition 수** | 10 (초기) → 50 (중기) → 500 (대규모) | 단계별 확장 |
| **복제 계수** | 3 | 가용성 보장 (최소 2개 ISR 유지) |
| **Retention** | 7일 (168시간) | 재처리 여유 기간 |
| **Compression** | snappy | 네트워크 대역폭 절약 |
| **Message Format** | JSON (CouponIssueRequest) | 스키마: userId, couponId, requestId, timestamp |

**Partition 수 결정 기준**:
```
처리 목표: 10,000 req/s (피크)
DB 처리 시간: ~50ms/req (비관적 락 포함)
Consumer 처리량: ~20 req/s (1000ms / 50ms)

필요 Consumer 수 = 10,000 / 20 = 500개
→ Partition 수 = 500개 (1 Consumer : 1 Partition)
```

**단계별 확장 전략**:
- **초기(Phase 1)**: 10 Partitions → 200 req/s 처리
- **중기(Phase 2)**: 50 Partitions → 1,000 req/s 처리
- **대규모(Phase 3)**: 500 Partitions → 10,000 req/s 처리

---

### 3.2 Partition 전략: userId 기반 파티셔닝

**파티셔닝 Key**: `userId` (String)

```
Partition Number = hash(userId) % Partition Count

예시:
userId=123 → hash(123) % 10 = 3 → Partition 3
userId=456 → hash(456) % 10 = 6 → Partition 6
```

**장점**:
- **순서 보장**: 같은 사용자의 요청은 항상 같은 Partition으로 전송
- **부하 분산**: Hash 함수로 균등 분배
- **독립 처리**: 다른 사용자 요청은 병렬 처리

**제약사항**:
- Partition 수를 증가시키면 기존 userId의 Partition 번호가 변경됨
- → 증설 시 전체 Consumer 재배치 필요 (Rebalancing)

---

### 3.3 Consumer Group 구성

| 항목 | 값 | 설명 |
|------|-----|------|
| **Group ID** | `ecommerce-coupon-consumer-group` | Consumer Group 식별자 |
| **Consumer 수** | Partition 수와 동일 | 최대 병렬도 달성 |
| **Concurrency** | 10 (Spring Kafka) | 10개 Consumer 스레드 |
| **Session Timeout** | 30초 | Heartbeat 실패 허용 시간 |
| **Max Poll Records** | 10 | 한 번에 처리할 메시지 수 |

**Consumer 배치 전략**:
```
10 Partitions + 10 Consumers:
  Consumer-0 → Partition 0
  Consumer-1 → Partition 1
  ...
  Consumer-9 → Partition 9

Consumer 증설 (20개):
  Consumer-0 → Partition 0
  Consumer-1 → Partition 1
  ...
  Consumer-9 → Partition 9
  Consumer-10~19 → Idle (대기)
```

**주의**: Partition 수보다 많은 Consumer는 Idle 상태 (처리 불가)

---

### 3.4 Offset 관리 전략

**커밋 모드**: **수동 커밋 (Manual Commit)**

**설정**:
```yaml
enable-auto-commit: false  # 자동 커밋 비활성화
ack-mode: MANUAL          # 수동 커밋 모드
```

**커밋 시점**:
1. **성공 시**: DB 트랜잭션 커밋 후 `acknowledgment.acknowledge()` 호출
2. **비즈니스 오류 시**: Offset 커밋 (재시도 불필요, skip)
3. **일시적 오류 시**: Offset 커밋 안 함 (재처리 보장)

**장점**:
- **at-least-once 보장**: 메시지 손실 방지
- **정확한 처리 보장**: 성공한 메시지만 커밋
- **재처리 가능**: 장애 발생 시 안전한 복구

**단점**:
- 중복 처리 가능 → DB UNIQUE 제약으로 멱등성 보장

---

### 3.5 Dead Letter Queue(DLQ) 설계

**목적**: 재시도해도 계속 실패하는 메시지 격리

**Topic 구성**:
```
Main Topic: coupon.issue.requests
DLQ Topic:  coupon.issue.requests.dlq
```

**DLQ 전송 기준**:
- 재시도 횟수 초과 (예: 3회)
- 파싱 불가능한 메시지 (JSON 형식 오류)
- 알 수 없는 예외 (UnknownException)

**운영 절차**:
1. DLQ 메시지 모니터링 (Kafka UI, Grafana)
2. 원인 분석 및 수정
3. 수동으로 Main Topic에 재발행
4. 또는 별도 배치 프로세스로 재처리

**현재 구현**: DLQ 미구현 (향후 추가 예정)
- Phase 1: 로깅만 수행
- Phase 2: Spring Kafka ErrorHandler + DLQ 자동 전송

---

## 4. 병렬 처리 및 동시성 제어

### 4.1 Kafka Partition 기반 병렬 처리

**아키텍처**:
```
                         ┌─ Partition 0 ─┐
                         │   Consumer 0   │──> DB (SELECT FOR UPDATE)
                         └────────────────┘
                         ┌─ Partition 1 ─┐
Client ──> Producer ──> │   Consumer 1   │──> DB (SELECT FOR UPDATE)
(10K req/s)             └────────────────┘
                         ┌─ Partition 2 ─┐
                         │   Consumer 2   │──> DB (SELECT FOR UPDATE)
                         └────────────────┘
                               ...
                         ┌─ Partition 9 ─┐
                         │   Consumer 9   │──> DB (SELECT FOR UPDATE)
                         └────────────────┘
```

**병렬 처리 수준**:
- **Producer 병렬**: 모든 요청을 비동기로 Kafka 발행
- **Kafka 병렬**: 10개 Partition에 메시지 분산 저장
- **Consumer 병렬**: 10개 Consumer가 독립적으로 처리
- **DB 병렬**: 10개 트랜잭션 동시 실행

**처리량 계산**:
```
단일 Consumer 처리량: 20 req/s
총 Consumer 수: 10개
총 처리량: 20 × 10 = 200 req/s
```

---

### 4.2 Partition 내 순서 보장

**Kafka 순서 보장 메커니즘**:
1. **Producer**: 같은 Key(userId)를 가진 메시지는 같은 Partition으로 전송
2. **Broker**: Partition 내에서 append-only 순서로 저장
3. **Consumer**: Partition 내 메시지를 순차적으로 poll

**선착순 보장 원리**:
```
시간 순서:  T1         T2         T3
요청:      User A     User B     User A
          (Coupon 1) (Coupon 1) (Coupon 2)

Partition 분배:
  Partition 3 (User A): [T1: Coupon 1] → [T3: Coupon 2]  ← 순서 유지
  Partition 6 (User B): [T2: Coupon 1]

처리 순서:
  Consumer 3: T1 → T3 (순서 보장)
  Consumer 6: T2 (독립 처리)
```

**제약사항**:
- **Partition 간 순서는 보장되지 않음**
- 다른 사용자(다른 Partition)의 요청 순서는 보장 불가
- → 선착순은 "Kafka 메시지 도착 순서" 기준 (타임스탬프 아님)

---

### 4.3 DB 비관적 락을 통한 재고 차감 정확성

**락 전략**: **Pessimistic Lock (SELECT FOR UPDATE)**

**SQL 예시**:
```sql
-- 1. 비관적 락으로 쿠폰 조회
SELECT * FROM coupon WHERE coupon_id = 1 FOR UPDATE;

-- 2. 재고 확인
IF issued_qty >= total_qty THEN
  ROLLBACK;
  THROW "쿠폰이 모두 소진되었습니다";
END IF;

-- 3. user_coupon 삽입 (UNIQUE 제약 체크)
INSERT INTO user_coupon (user_id, coupon_id, ...)
VALUES (123, 1, ...);

-- 4. 재고 차감
UPDATE coupon SET issued_qty = issued_qty + 1 WHERE coupon_id = 1;

-- 5. 커밋
COMMIT;
```

**동시성 제어 흐름**:
```
Consumer 1: SELECT FOR UPDATE (Coupon 1) ──> Lock 획득 ──> 처리 ──> COMMIT ──> Lock 해제
Consumer 2: SELECT FOR UPDATE (Coupon 1) ────────────────> 대기 ──────────────> Lock 획득
```

**장점**:
- **정확성**: 동시 요청에서도 재고 초과 발급 방지
- **단순성**: 애플리케이션 로직 단순화 (락 관리 불필요)

**단점**:
- **성능 저하**: 락 대기 시간으로 인한 처리 시간 증가
- **데드락 위험**: 락 순서 관리 필요

---

### 4.4 Kafka + DB 이중 동시성 제어 이유

**계층별 역할 분리**:

| 계층 | 동시성 제어 메커니즘 | 역할 |
|------|---------------------|------|
| **Kafka** | Partition 기반 병렬 처리 | **처리량 확장** (수평 확장) |
| **DB** | 비관적 락 (SELECT FOR UPDATE) | **정확성 보장** (재고 일관성) |

**왜 Kafka만으로 부족한가?**:
- Kafka는 메시지 **순서만 보장**, 재고 차감 **원자성은 보장 안 함**
- Consumer 여러 개가 동시에 DB를 업데이트하면 Race Condition 발생
- 예: 재고 1개 남았는데 2개 Consumer가 동시에 읽으면 2개 발급 가능

**왜 DB 락만으로 부족한가?**:
- DB 락은 확장성이 제한적 (단일 DB 인스턴스 한계)
- 락 경합이 심하면 처리량 급격히 감소
- Kafka 없이는 10,000 req/s 처리 불가

**결론**:
- **Kafka**: 요청을 분산 처리 (병렬성 ↑)
- **DB 락**: 재고 차감 정확성 보장 (일관성 ↑)
- → **두 메커니즘의 조합으로 처리량과 정확성 동시 달성**

---

## 5. 기존 시스템 대비 개선점

### 5.1 Redis Queue vs Kafka 비교

| 항목 | Redis Queue | Kafka | 개선 효과 |
|------|-------------|-------|----------|
| **처리량** | ~1,000 req/s (단일 인스턴스) | 10,000+ req/s (Partition 확장) | **10배 향상** |
| **확장성** | Vertical (인스턴스 스펙 증설) | Horizontal (Partition/Consumer 증설) | **선형 확장** |
| **장애 복구** | Redis Sentinel (복잡) | Replica + Offset (단순) | **운영 단순화** |
| **메시지 보관** | 휘발성 (메모리) | 영구 보관 (Disk, 7일) | **재처리 가능** |
| **순서 보장** | 단일 큐 (전역 순서) | Partition 내 순서 | **부분 순서** |
| **중복 처리** | 애플리케이션 로직 필요 | at-least-once + DB 제약 | **멱등성 보장** |
| **모니터링** | Redis CLI (제한적) | Kafka UI, JMX (풍부) | **가시성 향상** |

---

### 5.2 처리량 개선

**Redis Queue 구조** (기존):
```
Client (10K req/s) ──> Redis Queue (병목) ──> Worker (순차 처리)
                         ↓
                    처리량 한계: ~1,000 req/s
```

**Kafka 구조** (개선):
```
Client (10K req/s) ──> Kafka Producer ──┬──> Partition 0 → Consumer 0 (20 req/s)
                                        ├──> Partition 1 → Consumer 1 (20 req/s)
                                        ├──> Partition 2 → Consumer 2 (20 req/s)
                                        ...
                                        └──> Partition 499 → Consumer 499 (20 req/s)
                                               ↓
                                        처리량: 500 × 20 = 10,000 req/s
```

**결과**:
- Redis: 1,000 req/s → Kafka: 10,000 req/s
- **10배 처리량 향상**

---

### 5.3 확장성 개선

**Redis Queue** (Vertical Scaling):
- 처리량 증가 방법: Redis 인스턴스 스펙 업그레이드
- 한계: 단일 인스턴스 CPU/메모리 한계
- 비용: 스펙 증가에 따라 비선형적으로 증가

**Kafka** (Horizontal Scaling):
- 처리량 증가 방법: Partition/Consumer 수 증가
- 한계: 거의 없음 (Partition 수천 개도 가능)
- 비용: 노드 추가로 선형적 증가

**확장 비교**:
```
Redis:  1K req/s → 2K req/s → 4K req/s (인스턴스 교체)
        ↑          ↑          ↑
      비용 2배    비용 5배    비용 10배 (비선형)

Kafka:  200 req/s → 1K req/s → 10K req/s (Partition 증설)
        (P=10)      (P=50)     (P=500)
          ↑          ↑          ↑
        비용 1배    비용 5배    비용 50배 (선형)
```

---

### 5.4 장애 복구 개선

**Redis Sentinel** (복잡):
- Master 장애 감지 → Slave 승격 → 애플리케이션 재연결
- 장애 시 큐 데이터 손실 가능 (메모리 휘발성)
- 복구 시간: 수십 초 ~ 수분

**Kafka Replication** (단순):
- Leader 장애 감지 → ISR 중 하나가 자동 승격
- 메시지 손실 없음 (Disk 영구 보관)
- 복구 시간: 수 초

**메시지 재처리**:
- Redis: 장애 시 큐에 있던 메시지 손실
- Kafka: Offset 기반 재처리로 손실 방지

---

### 5.5 운영 안정성 개선

**모니터링**:
- Redis: `INFO` 커맨드, CloudWatch (제한적)
- Kafka: Kafka UI, JMX, Prometheus + Grafana (풍부)

**알림**:
- Redis: Queue depth, Memory usage
- Kafka: Consumer lag, Partition lag, Replication lag

**운영 편의성**:
- Redis: 수동 개입 필요 (Sentinel 관리)
- Kafka: 자동화된 장애 복구 (Controller 역할)

---

## 6. 확장 전략

### 6.1 Partition 수 vs Consumer 수 관계

**기본 원칙**:
- **Consumer 수 ≤ Partition 수**: 모든 Consumer가 작업
- **Consumer 수 > Partition 수**: 일부 Consumer Idle

**최적 구성**:
```
Partition 수 = Consumer 수 = 목표 처리량 / 단일 Consumer 처리량

예시:
목표: 10,000 req/s
단일 Consumer: 20 req/s
→ Partition 500개 + Consumer 500개
```

**Rebalancing**:
- Consumer 추가/제거 시 Partition 재배치
- 재배치 중 일시적 처리 중단 (수 초)
- 운영 시간대 피해 수행 권장

---

### 6.2 단계별 확장 시나리오

#### Phase 1: 초기 (200 req/s)
```
Partition: 10개
Consumer: 10개 (Spring Kafka concurrency=10)
Broker: 3대 (복제 계수 3)

처리량: 10 × 20 req/s = 200 req/s
인프라 비용: 저 (Kafka 3대 + DB 1대)
```

**적용 시점**: 서비스 초기, 중소 트래픽

---

#### Phase 2: 성장 (1,000 req/s)
```
Partition: 50개
Consumer: 50개 (Pod Replicas 5개 × concurrency=10)
Broker: 5대

처리량: 50 × 20 req/s = 1,000 req/s
인프라 비용: 중 (Kafka 5대 + DB 2대 Read Replica)

확장 방법:
1. Kafka Admin으로 Partition 10 → 50 증설
2. Consumer Pod Replicas 1 → 5 증가
3. DB Read Replica 추가 (조회 부하 분산)
```

**적용 시점**: 사용자 증가, 프로모션 대비

---

#### Phase 3: 대규모 (10,000 req/s)
```
Partition: 500개
Consumer: 500개 (Pod Replicas 50개 × concurrency=10)
Broker: 10대

처리량: 500 × 20 req/s = 10,000 req/s
인프라 비용: 고 (Kafka 10대 + DB Sharding)

확장 방법:
1. Partition 50 → 500 증설
2. Consumer Pod Replicas 5 → 50 증가
3. DB Sharding (coupon_id 기준 분산)
4. Redis Cluster (쿠폰 캐시 분산)

추가 최적화:
- Kafka Producer 배치 크기 증가
- Consumer prefetch 설정 튜닝
- DB Connection Pool 확대
```

**적용 시점**: 대규모 이벤트, 전국 프로모션

---

### 6.3 확장 시 주의사항

**Partition 수 변경**:
- ⚠️ Partition 수를 늘릴 수는 있지만 **줄일 수는 없음**
- → 과도한 증설 피하기 (관리 복잡도 증가)
- → 단계적 확장 권장 (10 → 50 → 500)

**Rebalancing 영향**:
- Consumer 추가/제거 시 전체 Consumer 재배치
- 재배치 중 메시지 처리 일시 중단 (수 초 ~ 수십 초)
- → 트래픽 낮은 시간대 수행

**비용 고려**:
- Partition 증가 = Broker 리소스 증가
- Consumer 증가 = Pod/VM 비용 증가
- → 처리량 목표와 비용 균형

---

## 7. 성능 목표 및 예상 결과

### 7.1 성능 목표

| 메트릭 | 목표 값 | 측정 방법 |
|--------|---------|----------|
| **처리량** | 10,000 req/s (피크) | Kafka Consumer lag 모니터링 |
| **응답 시간** | < 10ms (Producer) | Controller 응답 시간 |
| **처리 시간** | < 100ms (Consumer) | DB 트랜잭션 시간 |
| **가용성** | 99.9% | Uptime 모니터링 |
| **메시지 손실** | 0% | at-least-once 보장 |
| **중복 발급** | 0% | DB UNIQUE 제약 |

---

### 7.2 예상 결과

**처리량 향상**:
- Redis Queue: 1,000 req/s → Kafka: 10,000 req/s
- **10배 향상**

**응답 시간 개선**:
- Redis Queue: ~50ms (동기) → Kafka: ~5ms (비동기)
- **90% 단축**

**장애 복구 시간**:
- Redis Sentinel: 30초 ~ 2분 → Kafka: 5초 이내
- **80% 단축**

**운영 복잡도**:
- Redis: Sentinel 관리, 메모리 모니터링
- Kafka: 자동화된 장애 복구, 풍부한 모니터링
- **운영 부담 감소**

---

## 8. 운영 고려사항

### 8.1 모니터링 메트릭

**Kafka 메트릭**:
- Consumer Lag: 처리 지연 시간
- Partition Lag: 각 Partition별 지연
- Throughput: 초당 메시지 처리량
- Replication Lag: Follower 복제 지연

**애플리케이션 메트릭**:
- DB 트랜잭션 시간
- 비즈니스 오류율 (쿠폰 소진, 중복 발급)
- 일시적 오류율 (DB timeout, network error)

**알림 기준**:
- Consumer Lag > 1,000 (경고)
- Consumer Lag > 10,000 (긴급)
- 비즈니스 오류율 > 10% (검토)

---

### 8.2 장애 시나리오 대응

**Kafka Broker 장애**:
- 자동 Leader 선출 (ISR 기반)
- 복구 시간: 5초 이내
- 대응: 없음 (자동 복구)

**Consumer 장애**:
- Rebalancing으로 다른 Consumer에 Partition 재배치
- 복구 시간: 10초 이내
- 대응: Pod 자동 재시작 (Kubernetes)

**DB 장애**:
- Consumer는 일시적 오류로 처리
- Offset 커밋 안 함 (재처리 보장)
- 대응: DB 복구 후 자동 재처리

---

## 9. 결론

### 9.1 핵심 설계 원칙

1. **Kafka Partition 기반 병렬 처리**: 수평 확장 가능한 구조
2. **userId 파티셔닝**: 같은 사용자 요청 순서 보장
3. **at-least-once + DB 멱등성**: 메시지 손실 방지 + 중복 발급 차단
4. **비관적 락**: 재고 차감 정확성 보장
5. **수동 Offset 커밋**: 성공한 메시지만 커밋

### 9.2 기대 효과

- **처리량**: 1,000 req/s → 10,000 req/s (10배 향상)
- **확장성**: 선형 확장 가능 (Partition 증설)
- **안정성**: 장애 자동 복구, 메시지 손실 방지
- **운영성**: 풍부한 모니터링, 자동화된 알림

### 9.3 향후 개선 방향

- **DLQ 구현**: 재시도 실패 메시지 격리
- **DB Sharding**: 재고 차감 병목 해소
- **캐시 최적화**: Redis Cluster로 읽기 부하 분산
- **메시지 압축**: Avro/Protobuf 도입으로 네트워크 효율 향상

