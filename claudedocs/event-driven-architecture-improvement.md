# 이벤트 기반 아키텍처 개선 설계 문서

## 📋 목차
1. [현행 구조 분석](#1-현행-구조-분석)
2. [개선 구조 설계 (Event-Driven)](#2-개선-구조-설계-event-driven)
3. [시퀀스 다이어그램](#3-시퀀스-다이어그램)
4. [최종 결론](#4-최종-결론)

---

## 1. 현행 구조 분석

### 1.1 주문 저장 로직 구조

현재 시스템은 **Transactional Outbox Pattern**을 사용하여 외부 시스템 연동을 처리하고 있습니다.

#### 주요 컴포넌트

| 컴포넌트 | 파일 위치 | 역할 |
|---------|----------|------|
| `OrderService` | `application/order/OrderService.java` | 주문 생성 진입점 (3단계 처리: 검증 → 트랜잭션 → 후처리) |
| `OrderTransactionService` | `application/order/OrderTransactionService.java` | 원자적 트랜잭션 처리 및 Outbox 메시지 저장 |
| `Outbox` | `domain/order/Outbox.java` | 외부 시스템 전송 메시지 엔티티 |
| `OutboxPollingService` | `application/order/OutboxPollingService.java` | 배치 스케줄러 (5초마다 Outbox 조회 및 발행) |
| `OutboxEventPublisher` | `application/order/OutboxEventPublisher.java` | 외부 시스템 메시지 발행 (현재: 로깅, 향후: Kafka/HTTP) |

#### 코드 흐름 상세

**Step 1: 주문 생성 요청** (`OrderService.createOrder()` - Line 101-146)
```java
// 3단계 처리
1. 검증 단계 (Line 102-119)
   - 사용자 존재 확인
   - 장바구니 아이템 조회
   - 쿠폰 검증

2. 트랜잭션 단계 (Line 121-140)
   → orderTransactionService.executeTransactionalOrder() 호출

3. 후처리 단계 (Line 143)
   → handlePostOrderProcessing() - 현재는 로깅만 수행
```

**Step 2: 트랜잭션 처리** (`OrderTransactionService.executeTransactionalOrderInternal()` - Line 232-368)
```java
@Transactional {
    // 주문 엔티티 생성 및 저장
    Order order = Order.createOrder(...);
    Order savedOrder = orderRepository.save(order);

    // 주문 아이템 저장
    saveOrderItems(savedOrder, ...);

    // 재고 차감
    deductProductStock(...);

    // 쿠폰 사용 처리
    processCoupon(...);

    // 💡 핵심: Outbox 메시지 저장 (Line 367)
    saveOrderCompletionEvent(savedOrder.getOrderId(), userId);

    return savedOrder;
} // ← 트랜잭션 커밋
```

**Step 3: Outbox 메시지 저장** (`saveOrderCompletionEvent()` - Line 381-385)
```java
private void saveOrderCompletionEvent(Long orderId, Long userId) {
    Outbox outbox = Outbox.createOutbox(orderId, userId, "ORDER_COMPLETED");
    outboxRepository.save(outbox);  // ← 트랜잭션 내부에서 Outbox 저장
    log.info("[OrderTransactionService] Outbox 메시지 저장: orderId={}, status=PENDING", orderId);
}
```

**Step 4: 배치 폴링 및 외부 발행** (`OutboxPollingService.pollAndSendMessages()`)
```java
@Scheduled(fixedRate = 5000)  // ← 5초마다 실행
public void pollAndSendMessages() {
    // STEP 1: PENDING 상태 메시지 조회 (최대 100개)
    List<Outbox> pendingMessages = outboxRepository.findByStatusOrderByCreatedAtAsc(
        OutboxStatus.PENDING,
        100
    );

    // STEP 2: 각 메시지 처리
    for (Outbox message : pendingMessages) {
        processMessage(message);  // ← 여기서 외부 시스템 호출
    }
}

private void processMessage(Outbox message) {
    try {
        // STEP 3a: 외부 시스템에 메시지 발행
        eventPublisher.publish(message);  // ← OutboxEventPublisher.publish() 호출

        // STEP 3b: 성공 시 SENT 상태로 업데이트
        message.markAsSent();
        message.setSentAt(LocalDateTime.now());
        outboxRepository.update(message);

    } catch (Exception e) {
        // STEP 3c: 실패 시 재시도 카운트 증가 및 FAILED 처리
        handleMessageFailure(message, e);
    }
}
```

**Step 5: 외부 시스템 발행** (`OutboxEventPublisher.publish()` - Line 41-65)
```java
public void publish(Outbox message) throws Exception {
    switch (message.getMessageType()) {
        case "ORDER_COMPLETED":
            publishOrderCompleted(message);  // ← 여기서 외부 API 호출 (현재: 로깅만)
            break;
        // ...
    }
}

private void publishOrderCompleted(Outbox message) throws Exception {
    // TODO: 실제 구현 (Line 85-95)
    // 방법 1: Kafka 발행
    // kafkaTemplate.send("order.completed", message).get();
    //
    // 방법 2: HTTP 호출 (데이터 플랫폼 전송)
    // restTemplate.postForObject(
    //     "http://data-platform/api/orders",
    //     new OrderCompletedEvent(message.getOrderId()),
    //     ApiResponse.class);

    // 현재: 로깅만 수행 (Line 97-99)
    log.info("[OutboxEventPublisher] ORDER_COMPLETED 이벤트를 배송 시스템으로 발행합니다 - orderId={}",
            message.getOrderId());
}
```

### 1.2 트랜잭션 구조

```
┌─────────────────────────────────────────────────────────────┐
│ @Transactional (OrderTransactionService)                    │
│                                                               │
│  1. Order 저장          → orders 테이블                       │
│  2. OrderItem 저장      → order_items 테이블                  │
│  3. 재고 차감           → products 테이블 (stock 감소)         │
│  4. 쿠폰 처리           → user_coupons 테이블                 │
│  5. Outbox 저장         → outbox 테이블 (status=PENDING)      │
│                                                               │
└─────────────────────────────────────────────────────────────┘
                         ↓ COMMIT
┌─────────────────────────────────────────────────────────────┐
│ 별도 스레드 (OutboxPollingService)                           │
│                                                               │
│  @Scheduled(fixedRate = 5000)  ← 5초마다 실행                │
│                                                               │
│  1. Outbox 조회 (status=PENDING)                             │
│  2. OutboxEventPublisher.publish() 호출                      │
│  3. 외부 시스템 전송 (Kafka/HTTP) ← 트랜잭션 외부             │
│  4. Outbox 업데이트 (status=SENT)                            │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 1.3 외부 API 호출 지점

**위치**: `OutboxEventPublisher.publishOrderCompleted()` (Line 81-100)

**현재 상태**:
- TODO 주석으로 구현 예정 (Line 85-95)
- 실제로는 로깅만 수행 (Line 97-99)
- 향후 데이터 플랫폼 전송을 위한 HTTP 호출 또는 Kafka 발행 예정

**호출 시점**:
- 주문 트랜잭션 커밋 **후** (트랜잭션 외부)
- 배치 스케줄러가 5초마다 Outbox를 조회하여 처리
- 비동기 처리이지만 **폴링 기반**으로 최대 5초 지연 발생

### 1.4 현행 구조의 문제점

#### ⚠️ 문제점 1: 배치 폴링 방식의 지연
```
주문 완료 → Outbox 저장 → 트랜잭션 커밋 → (최대 5초 대기) → 배치 폴링 → 외부 전송
                                        ↑
                                    지연 구간
```
- 외부 시스템에 이벤트가 전달되기까지 **최대 5초 지연** 발생
- 실시간성이 중요한 시스템에서는 문제 가능

#### ⚠️ 문제점 2: 스케줄러 리소스 낭비
```
매 5초마다 실행:
- Outbox 테이블 전체 스캔 (status=PENDING 조회)
- 메시지가 없어도 계속 폴링
- DB 부하 및 스레드 자원 소모
```

#### ⚠️ 문제점 3: 확장성 제한
```java
@Scheduled(fixedRate = 5000)  // ← 단일 스레드 처리
public void pollAndSendMessages() {
    List<Outbox> pendingMessages = outboxRepository.findByStatusOrderByCreatedAtAsc(
        OutboxStatus.PENDING,
        100  // ← 배치 크기 고정
    );

    for (Outbox message : pendingMessages) {
        processMessage(message);  // ← 순차 처리
    }
}
```
- 단일 스레드 순차 처리로 대량 메시지 처리 시 병목 발생
- 배치 크기(100개) 초과 시 다음 폴링까지 대기

#### ⚠️ 문제점 4: 트랜잭션 경계 불명확
```
현재 구조:
- 주문 트랜잭션: Order + OrderItem + 재고 + 쿠폰 + Outbox
- 외부 전송 실패 시: Outbox만 FAILED 상태로 업데이트

문제:
- 외부 전송 실패가 주문 트랜잭션과 무관하게 처리됨
- 재시도 로직이 별도로 관리됨 (복잡도 증가)
```

#### ✅ 장점 (현행 구조)

1. **트랜잭션 안전성**: Outbox가 주문과 동일 트랜잭션에 저장되므로 메시지 유실 없음
2. **외부 시스템 장애 격리**: 외부 시스템 장애가 주문 트랜잭션에 영향 없음
3. **재시도 가능**: 실패한 메시지를 재시도할 수 있음 (Outbox 기반)

---

## 2. 개선 구조 설계 (Event-Driven)

### 2.1 개선 목표

1. **즉시 처리**: 배치 폴링 방식 제거 → 트랜잭션 커밋 직후 즉시 이벤트 발행
2. **리소스 효율**: 스케줄러 제거 → 이벤트가 발생할 때만 처리
3. **확장성 향상**: Spring의 비동기 이벤트 리스너 활용 → 병렬 처리 가능
4. **명확한 관심사 분리**: 주문 트랜잭션 vs 외부 시스템 연동 명확히 분리

### 2.2 Spring Event 기반 아키텍처

#### 핵심 컴포넌트

| 컴포넌트 | 역할 | 구현 방식 |
|---------|------|----------|
| `OrderCompletedEvent` | 주문 완료 도메인 이벤트 | POJO 클래스 (orderId, userId, timestamp 포함) |
| `ApplicationEventPublisher` | 이벤트 발행자 (Spring 기본 제공) | `@Autowired` 주입하여 사용 |
| `OrderEventListener` | 이벤트 수신 및 처리 | `@EventListener` 또는 `@TransactionalEventListener` |
| `ExternalSystemPublisher` | 외부 시스템 연동 (Kafka/HTTP) | 기존 `OutboxEventPublisher` 재사용 |

#### 개선 후 흐름

```
┌─────────────────────────────────────────────────────────────┐
│ @Transactional (OrderTransactionService)                    │
│                                                               │
│  1. Order 저장                                                │
│  2. OrderItem 저장                                            │
│  3. 재고 차감                                                 │
│  4. 쿠폰 처리                                                 │
│  5. Outbox 저장 (여전히 저장, 백업/감사 목적)                  │
│  6. 이벤트 발행 (메모리)                                       │
│     → applicationEventPublisher.publishEvent(               │
│           new OrderCompletedEvent(orderId, userId)          │
│       )                                                      │
│                                                               │
└─────────────────────────────────────────────────────────────┘
                         ↓ COMMIT
┌─────────────────────────────────────────────────────────────┐
│ @TransactionalEventListener (OrderEventListener)            │
│                                                               │
│  phase = AFTER_COMMIT  ← 트랜잭션 커밋 직후 즉시 실행         │
│                                                               │
│  1. 이벤트 수신 (OrderCompletedEvent)                         │
│  2. 외부 시스템 연동                                          │
│     - Kafka 발행                                             │
│     - HTTP API 호출 (데이터 플랫폼)                           │
│  3. Outbox 상태 업데이트 (SENT/FAILED)                        │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 이벤트 설계

#### 사용할 이벤트 명

| 이벤트 이름 | 발행 시점 | 페이로드 |
|-----------|---------|---------|
| `OrderCompletedEvent` | 주문 트랜잭션 커밋 직후 | orderId, userId, totalAmount, timestamp |
| `OrderCancelledEvent` | 주문 취소 트랜잭션 커밋 직후 | orderId, userId, reason, timestamp |
| `PaymentCompletedEvent` | 결제 완료 트랜잭션 커밋 직후 | orderId, userId, paymentMethod, timestamp |

#### 이벤트 발행 시점

**Before (현행)**:
```java
@Transactional
public Order executeTransactionalOrder(...) {
    // 주문 처리
    saveOrderCompletionEvent(orderId, userId);  // Outbox 저장만
    return order;
}  // ← 커밋

// (5초 후)
@Scheduled(fixedRate = 5000)
public void pollAndSendMessages() {
    // Outbox 조회 및 외부 전송
}
```

**After (개선)**:
```java
@Transactional
public Order executeTransactionalOrder(...) {
    // 주문 처리
    saveOrderCompletionEvent(orderId, userId);  // Outbox 저장 (백업)

    // 이벤트 발행 (메모리 기반, 트랜잭션 커밋 후 자동 발행)
    applicationEventPublisher.publishEvent(
        new OrderCompletedEvent(orderId, userId, totalAmount)
    );

    return order;
}  // ← 커밋

// (즉시 실행)
@Async  // 비동기 처리
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleOrderCompleted(OrderCompletedEvent event) {
    // 외부 시스템 연동 (Kafka/HTTP)
    externalSystemPublisher.publish(event);
}
```

### 2.4 리스너 책임 분리

#### OrderEventListener (새로 추가)

**책임**:
1. Spring 이벤트 수신 (`@TransactionalEventListener`)
2. 외부 시스템 연동 호출
3. 재시도 로직 처리
4. Outbox 상태 업데이트 (감사 목적)

**구현 방향**:
```java
@Component
@Slf4j
public class OrderEventListener {

    private final ExternalSystemPublisher externalSystemPublisher;
    private final OutboxRepository outboxRepository;

    /**
     * 주문 완료 이벤트 처리
     *
     * phase = AFTER_COMMIT: 주문 트랜잭션 커밋 직후 실행
     * @Async: 별도 스레드에서 비동기 처리 (주문 응답 속도에 영향 없음)
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        log.info("[OrderEventListener] OrderCompletedEvent 수신 - orderId={}",
                event.getOrderId());

        try {
            // 1. 외부 시스템 연동 (Kafka, HTTP)
            externalSystemPublisher.publishOrderCompleted(event);

            // 2. Outbox 상태 업데이트 (SENT)
            updateOutboxStatus(event.getOrderId(), OutboxStatus.SENT);

            log.info("[OrderEventListener] 외부 전송 성공 - orderId={}",
                    event.getOrderId());

        } catch (Exception e) {
            // 3. 실패 시 Outbox 상태 업데이트 (FAILED)
            updateOutboxStatus(event.getOrderId(), OutboxStatus.FAILED);

            log.error("[OrderEventListener] 외부 전송 실패 - orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);

            // 4. 재시도 로직은 별도 복구 메커니즘으로 처리
            // (예: Outbox 기반 배치 재시도, Dead Letter Queue)
        }
    }

    private void updateOutboxStatus(Long orderId, OutboxStatus status) {
        // Outbox 조회 및 상태 업데이트
        // (새로운 트랜잭션으로 처리 - REQUIRES_NEW)
    }
}
```

#### ExternalSystemPublisher (기존 OutboxEventPublisher 개선)

**책임**:
1. 외부 시스템별 발행 로직 캡슐화
2. Kafka, HTTP, 이메일 등 전송 채널 관리
3. 전송 실패 시 예외 발생 (재시도는 리스너에서 처리)

**개선 방향**:
```java
@Service
public class ExternalSystemPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate;

    /**
     * 주문 완료 이벤트를 외부 시스템에 발행
     *
     * @param event 주문 완료 이벤트
     * @throws Exception 발행 실패 시
     */
    public void publishOrderCompleted(OrderCompletedEvent event) throws Exception {
        // 방법 1: Kafka 발행
        kafkaTemplate.send("order.completed",
                         String.valueOf(event.getOrderId()),
                         event)
                    .get(5, TimeUnit.SECONDS);  // 타임아웃 설정

        // 방법 2: 데이터 플랫폼 HTTP API 호출
        DataPlatformRequest request = DataPlatformRequest.builder()
            .orderId(event.getOrderId())
            .userId(event.getUserId())
            .totalAmount(event.getTotalAmount())
            .timestamp(event.getTimestamp())
            .build();

        restTemplate.postForObject(
            "http://data-platform/api/orders/completed",
            request,
            ApiResponse.class
        );

        log.info("[ExternalSystemPublisher] 데이터 플랫폼 전송 완료 - orderId={}",
                event.getOrderId());
    }
}
```

### 2.5 트랜잭션 경계 설계

#### 트랜잭션 경계 분리

```
┌──────────────────────────────────────────────────┐
│ Transaction 1: 주문 처리                          │
│ Propagation: REQUIRED                            │
│                                                    │
│  - Order 저장                                      │
│  - OrderItem 저장                                  │
│  - 재고 차감                                       │
│  - 쿠폰 처리                                       │
│  - Outbox 저장 (status=PENDING)                   │
│  - Event 발행 (메모리)                             │
│                                                    │
│  ✅ COMMIT                                         │
└──────────────────────────────────────────────────┘
                    ↓
        @TransactionalEventListener
        phase = AFTER_COMMIT
                    ↓
┌──────────────────────────────────────────────────┐
│ Non-Transactional: 외부 시스템 연동               │
│ (또는 별도 트랜잭션)                               │
│                                                    │
│  - Kafka 발행                                     │
│  - HTTP API 호출                                  │
│                                                    │
│  성공 시:                                          │
│  ┌────────────────────────────────────────┐      │
│  │ Transaction 2: Outbox 업데이트           │      │
│  │ Propagation: REQUIRES_NEW               │      │
│  │  - Outbox 상태 → SENT                   │      │
│  │  - sentAt 타임스탬프 기록                │      │
│  └────────────────────────────────────────┘      │
│                                                    │
│  실패 시:                                          │
│  ┌────────────────────────────────────────┐      │
│  │ Transaction 3: Outbox 업데이트           │      │
│  │ Propagation: REQUIRES_NEW               │      │
│  │  - Outbox 상태 → FAILED                 │      │
│  │  - retryCount 증가                      │      │
│  │  - errorMessage 기록                    │      │
│  └────────────────────────────────────────┘      │
│                                                    │
└──────────────────────────────────────────────────┘
```

#### 트랜잭션 전파 설정

| 트랜잭션 | Propagation | 이유 |
|---------|------------|------|
| 주문 처리 | REQUIRED (기본값) | Order + Outbox 원자성 보장 |
| 외부 연동 | Non-Transactional | 외부 시스템 지연이 주문 트랜잭션에 영향 없도록 |
| Outbox 업데이트 | REQUIRES_NEW | 외부 연동 성공/실패와 무관하게 상태 기록 |

### 2.6 재시도 전략

#### Level 1: 즉시 재시도 (Event Listener)

```java
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleOrderCompleted(OrderCompletedEvent event) {
    int maxRetries = 3;
    int retryCount = 0;

    while (retryCount < maxRetries) {
        try {
            externalSystemPublisher.publishOrderCompleted(event);
            updateOutboxStatus(event.getOrderId(), OutboxStatus.SENT);
            return;  // 성공

        } catch (Exception e) {
            retryCount++;
            if (retryCount >= maxRetries) {
                // 최종 실패
                updateOutboxStatus(event.getOrderId(), OutboxStatus.FAILED);
                log.error("[OrderEventListener] 최종 실패 - orderId={}, retries={}",
                        event.getOrderId(), retryCount);
            } else {
                // 재시도
                log.warn("[OrderEventListener] 재시도 중 - orderId={}, attempt={}",
                        event.getOrderId(), retryCount);
                Thread.sleep(1000 * retryCount);  // Exponential backoff
            }
        }
    }
}
```

#### Level 2: 배치 복구 (기존 OutboxPollingService 유지)

```java
/**
 * 실패한 메시지 배치 재처리
 *
 * Event Listener에서 3번 재시도 실패한 메시지를
 * 별도 배치 프로세스로 복구 시도
 */
@Scheduled(fixedRate = 300000)  // 5분마다
public void retryFailedMessages() {
    List<Outbox> failedMessages = outboxRepository.findByStatus(
        OutboxStatus.FAILED,
        100
    );

    for (Outbox message : failedMessages) {
        // 최대 재시도 횟수 제한
        if (message.getRetryCount() < 10) {
            try {
                // 이벤트 재발행
                publishEvent(message);

            } catch (Exception e) {
                message.incrementRetryCount();
                outboxRepository.update(message);
            }
        }
    }
}
```

### 2.7 장애 격리 전략

#### 격리 메커니즘

| 격리 레벨 | 메커니즘 | 효과 |
|---------|---------|------|
| **트랜잭션 격리** | `@TransactionalEventListener(AFTER_COMMIT)` | 외부 시스템 장애가 주문 트랜잭션 롤백 유발 없음 |
| **스레드 격리** | `@Async` | 외부 시스템 지연이 주문 응답 시간에 영향 없음 |
| **상태 격리** | Outbox 상태 관리 (PENDING/SENT/FAILED) | 실패한 메시지 추적 및 복구 가능 |
| **데이터 격리** | REQUIRES_NEW 전파 | Outbox 업데이트 실패가 외부 연동에 영향 없음 |

#### Circuit Breaker 패턴 (선택적)

```java
@Service
public class ExternalSystemPublisher {

    // Resilience4j Circuit Breaker
    @CircuitBreaker(name = "dataPlatform", fallbackMethod = "fallbackPublish")
    public void publishOrderCompleted(OrderCompletedEvent event) throws Exception {
        restTemplate.postForObject(
            "http://data-platform/api/orders/completed",
            event,
            ApiResponse.class
        );
    }

    /**
     * Circuit Open 시 fallback 처리
     * - 일시적으로 Outbox만 PENDING 상태로 유지
     * - 외부 시스템 복구 후 배치로 재처리
     */
    private void fallbackPublish(OrderCompletedEvent event, Exception e) {
        log.warn("[ExternalSystemPublisher] Circuit Open - orderId={}, 배치 재처리 대기",
                event.getOrderId());
        // Outbox는 PENDING 상태로 유지 → 배치 재처리
    }
}
```

### 2.8 장단점 비교

#### ✅ 개선 구조의 장점

| 항목 | 현행 (Batch Polling) | 개선 (Event-Driven) |
|-----|---------------------|-------------------|
| **지연 시간** | 최대 5초 | 즉시 (커밋 직후) |
| **리소스 효율** | 5초마다 DB 폴링 (메시지 없어도) | 이벤트 발생 시만 처리 |
| **확장성** | 단일 스레드 순차 처리 | 비동기 병렬 처리 가능 |
| **코드 복잡도** | 스케줄러 + 폴링 로직 | 이벤트 발행 + 리스너 |
| **테스트 용이성** | 스케줄러 Mock 필요 | 이벤트 발행 검증 간단 |
| **모니터링** | Outbox 테이블 조회 | Spring Event 메트릭 활용 |

#### ⚠️ 개선 구조의 단점 및 고려사항

1. **메모리 기반 이벤트의 유실 가능성**
   - 문제: 이벤트 발행 후 애플리케이션 재시작 시 미처리 이벤트 유실
   - 해결: Outbox를 백업으로 유지 → 배치 복구 프로세스 병행

2. **동시성 제어 복잡도**
   - 문제: 같은 주문에 대한 중복 이벤트 처리 가능성
   - 해결: 멱등성 키(Idempotency Key) 사용 또는 Outbox 기반 중복 방지

3. **트랜잭션 경계 복잡도 증가**
   - 문제: REQUIRES_NEW 전파로 인한 트랜잭션 이해 어려움
   - 해결: 명확한 문서화 및 트랜잭션 모니터링

4. **디버깅 어려움**
   - 문제: 비동기 이벤트 흐름 추적 복잡
   - 해결: MDC(Mapped Diagnostic Context)로 요청 ID 전파, 분산 추적 도구 활용

#### 💡 하이브리드 접근 (권장)

```
실시간 처리: Spring Event → 즉시 외부 전송
  ↓ 실패 시
백업 처리: Outbox 기반 배치 재시도 (5분마다)
  ↓ 최종 실패 시
수동 복구: DLQ(Dead Letter Queue) 관리 도구
```

---

## 3. 시퀀스 다이어그램

### 3.1 현행 구조 (Batch Polling)

```mermaid
sequenceDiagram
    participant Client as 클라이언트
    participant OrderService as OrderService
    participant OrderTxService as OrderTransactionService
    participant DB as Database
    participant Scheduler as OutboxPollingService<br/>(Scheduler)
    participant Publisher as OutboxEventPublisher
    participant External as 외부 시스템<br/>(데이터 플랫폼)

    Client->>OrderService: POST /orders (주문 생성)
    activate OrderService

    OrderService->>OrderTxService: executeTransactionalOrder()
    activate OrderTxService

    Note over OrderTxService: @Transactional 시작

    OrderTxService->>DB: Order 저장
    OrderTxService->>DB: OrderItem 저장
    OrderTxService->>DB: 재고 차감 (Product)
    OrderTxService->>DB: 쿠폰 처리 (UserCoupon)

    OrderTxService->>OrderTxService: saveOrderCompletionEvent()
    OrderTxService->>DB: Outbox 저장<br/>(status=PENDING)

    Note over OrderTxService: @Transactional COMMIT

    OrderTxService-->>OrderService: Order 반환
    deactivate OrderTxService

    OrderService-->>Client: 201 Created<br/>(주문 완료)
    deactivate OrderService

    Note over Scheduler,External: 최대 5초 대기...

    Scheduler->>Scheduler: @Scheduled(fixedRate=5000)<br/>pollAndSendMessages()
    activate Scheduler

    Scheduler->>DB: SELECT * FROM outbox<br/>WHERE status='PENDING'<br/>LIMIT 100
    DB-->>Scheduler: [Outbox 메시지 리스트]

    loop 각 메시지 처리
        Scheduler->>Publisher: publish(outbox)
        activate Publisher

        Publisher->>External: POST /api/orders/completed<br/>(HTTP 또는 Kafka)
        activate External

        alt 성공
            External-->>Publisher: 200 OK
            deactivate External
            Publisher-->>Scheduler: 성공
            deactivate Publisher

            Scheduler->>DB: UPDATE outbox<br/>SET status='SENT'<br/>WHERE message_id=?

        else 실패
            External-->>Publisher: 500 Error
            deactivate External
            Publisher-->>Scheduler: Exception
            deactivate Publisher

            Scheduler->>DB: UPDATE outbox<br/>SET status='FAILED',<br/>retry_count=retry_count+1
        end
    end

    deactivate Scheduler

    Note over Scheduler: 5초 후 다시 폴링...
```

#### 현행 구조 흐름 설명

1. **주문 생성 요청** (Line 1-3)
   - 클라이언트가 `POST /orders` 요청
   - OrderService가 요청 수신

2. **트랜잭션 처리** (Line 5-17)
   - `@Transactional` 시작
   - Order, OrderItem, 재고, 쿠폰, Outbox 모두 동일 트랜잭션에서 저장
   - Outbox 상태는 `PENDING`으로 저장
   - 트랜잭션 커밋

3. **주문 응답** (Line 19-20)
   - 클라이언트에게 `201 Created` 응답
   - **외부 시스템 전송과 무관하게 주문 완료**

4. **배치 폴링 대기** (Line 22)
   - 최대 5초 대기 (스케줄러 주기)

5. **배치 처리** (Line 24-48)
   - 5초마다 `@Scheduled` 메서드 실행
   - Outbox 테이블에서 `PENDING` 메시지 조회 (최대 100개)
   - 각 메시지를 순차 처리:
     - 외부 시스템에 HTTP/Kafka 전송
     - 성공 시: Outbox → `SENT`
     - 실패 시: Outbox → `FAILED`, retryCount 증가

6. **반복** (Line 50)
   - 5초 후 다시 폴링 시작

#### 현행 구조의 문제점 (다이어그램 관점)

- **지연**: Line 22의 대기 시간 (최대 5초)
- **리소스 낭비**: Line 26-27에서 메시지 없어도 DB 조회
- **순차 처리**: Line 29의 loop가 단일 스레드 순차 처리

---

### 3.2 개선 구조 (Event-Driven)

```mermaid
sequenceDiagram
    participant Client as 클라이언트
    participant OrderService as OrderService
    participant OrderTxService as OrderTransactionService
    participant EventPublisher as ApplicationEventPublisher
    participant DB as Database
    participant EventListener as OrderEventListener<br/>(@Async)
    participant ExternalPublisher as ExternalSystemPublisher
    participant External as 외부 시스템<br/>(데이터 플랫폼)

    Client->>OrderService: POST /orders (주문 생성)
    activate OrderService

    OrderService->>OrderTxService: executeTransactionalOrder()
    activate OrderTxService

    Note over OrderTxService: @Transactional 시작

    OrderTxService->>DB: Order 저장
    OrderTxService->>DB: OrderItem 저장
    OrderTxService->>DB: 재고 차감 (Product)
    OrderTxService->>DB: 쿠폰 처리 (UserCoupon)

    OrderTxService->>OrderTxService: saveOrderCompletionEvent()
    OrderTxService->>DB: Outbox 저장<br/>(status=PENDING, 백업용)

    OrderTxService->>EventPublisher: publishEvent(<br/>OrderCompletedEvent)
    Note over EventPublisher: 이벤트 메모리 저장<br/>(트랜잭션 커밋 시 발행)

    Note over OrderTxService: @Transactional COMMIT

    OrderTxService-->>OrderService: Order 반환
    deactivate OrderTxService

    OrderService-->>Client: 201 Created<br/>(주문 완료)
    deactivate OrderService

    Note over EventPublisher,EventListener: 트랜잭션 커밋 직후 즉시 발행

    EventPublisher->>EventListener: OrderCompletedEvent<br/>(phase=AFTER_COMMIT)
    activate EventListener

    Note over EventListener: @Async 비동기 처리<br/>(별도 스레드)

    EventListener->>ExternalPublisher: publishOrderCompleted(event)
    activate ExternalPublisher

    ExternalPublisher->>External: POST /api/orders/completed<br/>(HTTP 또는 Kafka)
    activate External

    alt 성공
        External-->>ExternalPublisher: 200 OK
        deactivate External
        ExternalPublisher-->>EventListener: 성공
        deactivate ExternalPublisher

        Note over EventListener: REQUIRES_NEW 트랜잭션
        EventListener->>DB: UPDATE outbox<br/>SET status='SENT',<br/>sent_at=NOW()

        EventListener-->>EventPublisher: 처리 완료
        deactivate EventListener

    else 실패 (재시도 3회)
        External-->>ExternalPublisher: 500 Error
        deactivate External
        ExternalPublisher-->>EventListener: Exception
        deactivate ExternalPublisher

        EventListener->>EventListener: 재시도 (최대 3회)<br/>Exponential Backoff

        alt 재시도 성공
            EventListener->>External: POST (재시도)
            External-->>EventListener: 200 OK
            EventListener->>DB: UPDATE outbox → SENT
        else 최종 실패
            Note over EventListener: REQUIRES_NEW 트랜잭션
            EventListener->>DB: UPDATE outbox<br/>SET status='FAILED',<br/>retry_count=3,<br/>error_message=?

            EventListener-->>EventPublisher: 실패 기록
            deactivate EventListener
        end
    end

    Note over DB,External: 백업 복구 메커니즘 (선택적)

    opt 배치 복구 (5분마다)
        activate EventListener
        EventListener->>DB: SELECT * FROM outbox<br/>WHERE status='FAILED'<br/>AND retry_count < 10
        DB-->>EventListener: [실패 메시지]

        EventListener->>External: POST (복구 시도)
        External-->>EventListener: 200 OK
        EventListener->>DB: UPDATE outbox → SENT
        deactivate EventListener
    end
```

#### 개선 구조 흐름 설명

1. **주문 생성 요청** (Line 1-3)
   - 클라이언트가 `POST /orders` 요청
   - OrderService가 요청 수신

2. **트랜잭션 처리 + 이벤트 발행** (Line 5-24)
   - `@Transactional` 시작
   - Order, OrderItem, 재고, 쿠폰 저장
   - **Outbox 저장 (백업/감사 목적)**
   - **이벤트 발행** (`publishEvent(OrderCompletedEvent)`)
     - 이벤트는 메모리에 저장 (트랜잭션 커밋 전)
   - 트랜잭션 커밋
     - **커밋 성공 시 자동으로 이벤트 발행**

3. **주문 응답** (Line 26-27)
   - 클라이언트에게 `201 Created` 응답
   - **이벤트 처리와 무관하게 즉시 응답**

4. **이벤트 리스너 즉시 실행** (Line 29-32)
   - `@TransactionalEventListener(phase=AFTER_COMMIT)` 트리거
   - **커밋 직후 즉시 실행 (지연 없음)**
   - `@Async`로 별도 스레드에서 비동기 처리

5. **외부 시스템 연동** (Line 34-38)
   - ExternalSystemPublisher를 통해 외부 전송
   - HTTP API 또는 Kafka 발행

6. **성공 처리** (Line 40-47)
   - 외부 시스템 응답 200 OK
   - **새로운 트랜잭션 (REQUIRES_NEW)**으로 Outbox 업데이트
   - Outbox 상태 → `SENT`, sentAt 타임스탬프 기록

7. **실패 처리 (재시도 포함)** (Line 49-68)
   - 외부 시스템 응답 500 Error
   - **즉시 재시도** (최대 3회, Exponential Backoff)
   - 재시도 성공 시: Outbox → `SENT`
   - 최종 실패 시: Outbox → `FAILED`, retryCount=3 기록

8. **배치 복구 (선택적)** (Line 70-80)
   - 5분마다 `FAILED` 상태 메시지 조회
   - 최대 10회까지 재시도
   - 성공 시 Outbox → `SENT`

#### 개선 구조의 핵심 차이점 (다이어그램 관점)

| 항목 | 현행 구조 | 개선 구조 |
|-----|---------|---------|
| **이벤트 발행 시점** | 없음 (Outbox만 저장) | 트랜잭션 내부 (Line 20-21) |
| **처리 시작 시점** | 최대 5초 대기 (Line 22) | 커밋 직후 즉시 (Line 32) |
| **처리 방식** | 동기 (단일 스레드 loop) | 비동기 (@Async, Line 34) |
| **재시도 전략** | 배치에서만 재시도 | 즉시 재시도 + 배치 복구 (Line 55-64) |
| **Outbox 역할** | 주 메커니즘 | 백업/감사 (Line 18) |

---

## 4. 최종 결론

### 4.1 왜 이벤트 기반 아키텍처로 가야 하는가?

#### 1️⃣ 실시간성 확보

**현행**: 주문 완료 → (최대 5초 대기) → 외부 전송
**개선**: 주문 완료 → (즉시) → 외부 전송

**비즈니스 임팩트**:
- **배송 시스템**: 주문 즉시 배송 준비 시작 가능 (5초 단축)
- **데이터 플랫폼**: 실시간 주문 통계 대시보드 정확도 향상
- **고객 경험**: 주문 완료 알림 즉시 발송 가능

**ROI 예시**:
```
일일 주문 10,000건 기준
- 평균 지연 감소: 2.5초 (0~5초 분포 가정)
- 총 절감 시간: 10,000 * 2.5초 = 6.9시간/일
- 실시간 데이터 활용도: 배치 기반 대비 99% 향상
```

#### 2️⃣ 리소스 효율성

**현행**: 5초마다 Outbox 테이블 전체 스캔 (메시지 없어도)
```sql
-- 매 5초마다 실행
SELECT * FROM outbox
WHERE status = 'PENDING'
ORDER BY created_at ASC
LIMIT 100;

-- 일일 실행 횟수: 86,400초 / 5초 = 17,280회
-- 메시지 없는 경우에도 17,280회 DB 조회
```

**개선**: 이벤트 발생 시에만 처리
```
일일 주문 10,000건 기준
- DB 조회: 17,280회 → 10,000회 (42% 감소)
- 스케줄러 스레드: 1개 → 0개 (이벤트 리스너로 대체)
- 메모리 사용: Outbox 배치 조회 제거
```

#### 3️⃣ 확장성

**현행**: 단일 스레드 순차 처리
```java
for (Outbox message : pendingMessages) {
    processMessage(message);  // 순차 처리
}

// 100개 메시지 처리 시간: 100 * 평균 응답시간 (예: 500ms) = 50초
// 다음 배치까지 대기해야 함
```

**개선**: 비동기 병렬 처리
```java
@Async("orderEventExecutor")  // 스레드 풀 설정
@TransactionalEventListener
public void handleOrderCompleted(OrderCompletedEvent event) {
    // 병렬 처리
}

// ThreadPoolTaskExecutor 설정
// - corePoolSize: 10
// - maxPoolSize: 50
// - queueCapacity: 1000

// 100개 메시지 처리 시간: 평균 응답시간 (500ms) - 병렬 처리
// 처리량: 10배 이상 증가 가능
```

**확장 시나리오**:
| 일일 주문량 | 현행 처리 시간 | 개선 처리 시간 | 개선율 |
|-----------|--------------|--------------|-------|
| 10,000건 | 83분 (순차) | 8분 (병렬 10) | 90% |
| 50,000건 | 417분 (순차) | 42분 (병렬 10) | 90% |
| 100,000건 | 833분 (순차) | 83분 (병렬 10) | 90% |

#### 4️⃣ 관심사 분리 (Clean Architecture)

**현행**: 트랜잭션 로직과 외부 연동이 시간적으로 분리되어 있지만 구조적으로 강결합

```java
// OrderTransactionService.java
@Transactional
public Order executeTransactionalOrder(...) {
    // 도메인 로직: 주문 저장
    Order order = orderRepository.save(order);

    // 인프라 로직: Outbox 저장 (외부 시스템 연동 준비)
    saveOrderCompletionEvent(orderId, userId);  // ← 관심사 혼재

    return order;
}

// OutboxPollingService.java - 별도 컴포넌트이지만 Outbox 테이블에 의존
@Scheduled(fixedRate = 5000)
public void pollAndSendMessages() {
    // 인프라 로직: Outbox 조회 및 외부 전송
}
```

**개선**: 이벤트를 통한 명확한 경계 분리

```java
// OrderTransactionService.java - 도메인/애플리케이션 계층
@Transactional
public Order executeTransactionalOrder(...) {
    // 도메인 로직: 주문 저장
    Order order = orderRepository.save(order);

    // 이벤트 발행: 도메인 이벤트 (관심사 명확)
    applicationEventPublisher.publishEvent(
        new OrderCompletedEvent(orderId, userId)  // ← 도메인 개념
    );

    return order;
}

// OrderEventListener.java - 인프라 계층
@Async
@TransactionalEventListener
public void handleOrderCompleted(OrderCompletedEvent event) {
    // 인프라 로직: 외부 시스템 연동
    externalSystemPublisher.publish(event);
}
```

**아키텍처 개선**:
```
┌─────────────────────────────────────────────┐
│ 도메인 계층 (Domain Layer)                    │
│  - Order, OrderItem (엔티티)                 │
│  - OrderCompletedEvent (도메인 이벤트)        │
└─────────────────────────────────────────────┘
                    ↓ 의존 방향
┌─────────────────────────────────────────────┐
│ 애플리케이션 계층 (Application Layer)          │
│  - OrderService, OrderTransactionService     │
│  - 이벤트 발행 (도메인 이벤트 → 인프라 이벤트)  │
└─────────────────────────────────────────────┘
                    ↓ 의존 방향
┌─────────────────────────────────────────────┐
│ 인프라 계층 (Infrastructure Layer)            │
│  - OrderEventListener (이벤트 수신)           │
│  - ExternalSystemPublisher (Kafka/HTTP)      │
│  - OutboxRepository (영속성)                 │
└─────────────────────────────────────────────┘
```

#### 5️⃣ 테스트 용이성

**현행**: 스케줄러 테스트 복잡
```java
@SpringBootTest
class OutboxPollingServiceTest {

    @Test
    void 배치_폴링_테스트() throws Exception {
        // Given: Outbox에 메시지 저장
        outboxRepository.save(outbox);

        // When: 스케줄러 실행 대기 (시간 제어 어려움)
        Thread.sleep(6000);  // 5초 스케줄 + 버퍼

        // Then: Outbox 상태 확인
        Outbox result = outboxRepository.findById(outbox.getMessageId());
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.SENT);
    }
}
```

**개선**: 이벤트 발행 검증 간단
```java
@SpringBootTest
class OrderTransactionServiceTest {

    @MockBean
    private ApplicationEventPublisher eventPublisher;

    @Test
    void 주문_완료시_이벤트_발행() {
        // When: 주문 생성
        Order order = orderTransactionService.executeTransactionalOrder(...);

        // Then: 이벤트 발행 검증 (즉시 확인 가능)
        verify(eventPublisher, times(1)).publishEvent(
            argThat(event ->
                event instanceof OrderCompletedEvent &&
                ((OrderCompletedEvent) event).getOrderId().equals(order.getOrderId())
            )
        );
    }
}

@SpringBootTest
class OrderEventListenerTest {

    @MockBean
    private ExternalSystemPublisher externalPublisher;

    @Test
    void 이벤트_수신시_외부_전송() {
        // Given: 이벤트 준비
        OrderCompletedEvent event = new OrderCompletedEvent(1L, 100L, 10000L);

        // When: 리스너 직접 호출 (스케줄러 대기 불필요)
        orderEventListener.handleOrderCompleted(event);

        // Then: 외부 전송 검증
        verify(externalPublisher, times(1)).publishOrderCompleted(event);
    }
}
```

#### 6️⃣ 모니터링 및 관찰성

**현행**: Outbox 테이블 조회 및 로그 분석
```sql
-- 처리 대기 중인 메시지 수
SELECT COUNT(*) FROM outbox WHERE status = 'PENDING';

-- 실패 메시지 조회
SELECT * FROM outbox WHERE status = 'FAILED' ORDER BY created_at DESC;

-- 평균 처리 시간 (sent_at - created_at)
SELECT AVG(TIMESTAMPDIFF(SECOND, created_at, sent_at)) FROM outbox WHERE status = 'SENT';
```

**개선**: Spring Boot Actuator + Micrometer 메트릭
```java
@Component
public class OrderEventListener {

    private final MeterRegistry meterRegistry;

    @Async
    @TransactionalEventListener
    public void handleOrderCompleted(OrderCompletedEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            externalSystemPublisher.publish(event);

            // 성공 카운터
            meterRegistry.counter("order.event.published",
                "type", "ORDER_COMPLETED",
                "status", "success"
            ).increment();

        } catch (Exception e) {
            // 실패 카운터
            meterRegistry.counter("order.event.published",
                "type", "ORDER_COMPLETED",
                "status", "failed"
            ).increment();

        } finally {
            // 처리 시간 기록
            sample.stop(meterRegistry.timer("order.event.processing.time",
                "type", "ORDER_COMPLETED"
            ));
        }
    }
}

// Prometheus/Grafana에서 실시간 모니터링 가능:
// - order_event_published_total{type="ORDER_COMPLETED",status="success"}
// - order_event_published_total{type="ORDER_COMPLETED",status="failed"}
// - order_event_processing_time_seconds{type="ORDER_COMPLETED"}
```

### 4.2 마이그레이션 전략 (단계별 접근)

#### Phase 1: 이벤트 추가 (하이브리드)
```
현재 구조 유지 + 이벤트 발행 추가
- Outbox 폴링 계속 동작 (안전망)
- 이벤트 리스너 추가 (병행 운영)
- 두 메커니즘 결과 비교 (A/B 테스트)
```

#### Phase 2: 트래픽 전환
```
이벤트 리스너로 점진적 전환
- 특정 주문 타입만 이벤트 처리 (예: VIP 고객)
- 성공률 모니터링 (목표: 99.9%)
- 문제 발생 시 Outbox 폴링으로 자동 복구
```

#### Phase 3: 스케줄러 제거
```
이벤트 리스너로 완전 전환 후
- Outbox 폴링 스케줄러 제거
- Outbox는 감사/백업 용도로만 유지
- 실패 메시지 복구용 배치만 유지 (5분 간격)
```

#### Phase 4: 최적화
```
성능 튜닝 및 모니터링 강화
- 스레드 풀 크기 최적화
- Circuit Breaker 임계값 조정
- 분산 추적 시스템 통합 (Zipkin/Jaeger)
```

### 4.3 최종 권장사항

#### ✅ 이벤트 기반 아키텍처 도입을 권장하는 이유

1. **실시간성**: 최대 5초 지연 제거 → 즉시 처리
2. **효율성**: DB 조회 42% 감소, 스케줄러 스레드 제거
3. **확장성**: 순차 처리 → 비동기 병렬 처리 (10배 처리량)
4. **관심사 분리**: Clean Architecture 원칙 준수
5. **테스트 용이성**: Mock 기반 단위 테스트 가능
6. **모니터링**: Spring Boot Actuator 메트릭 활용

#### ⚠️ 주의사항

1. **Outbox 유지**: 백업 및 감사 추적 목적으로 Outbox 테이블 유지
2. **멱등성 보장**: 중복 이벤트 처리 방지 (Idempotency Key)
3. **트랜잭션 이해**: REQUIRES_NEW 전파 메커니즘 숙지
4. **모니터링 필수**: 비동기 처리 흐름 추적 도구 구축
5. **점진적 마이그레이션**: 하이브리드 접근으로 리스크 최소화

#### 💡 핵심 메시지

> **배치 폴링 방식(Outbox Pattern)은 안전하지만 느립니다.**
> **이벤트 기반 아키텍처는 빠르고 효율적이며 확장 가능합니다.**
> **두 메커니즘을 조합하면 안전성과 성능을 모두 확보할 수 있습니다.**

```
주문 트랜잭션 → Outbox 저장 (백업) + Event 발행 (실시간 처리)
                     ↓                        ↓
              배치 복구 (실패 시)      즉시 외부 전송 (성공 시)

= 안전성 (Outbox) + 실시간성 (Event) = 최적의 하이브리드 구조
```

---

## 참고 문서

### 관련 코드 파일
- `OrderService.java` (Line 101-166): 주문 생성 3단계 처리
- `OrderTransactionService.java` (Line 232-385): 트랜잭션 및 Outbox 저장
- `OutboxPollingService.java` (Line 54-106): 배치 폴링 스케줄러
- `OutboxEventPublisher.java` (Line 41-146): 외부 시스템 발행

### 참고 패턴
- **Transactional Outbox Pattern**: https://microservices.io/patterns/data/transactional-outbox.html
- **Spring Events**: https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events
- **@TransactionalEventListener**: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html

---

**문서 작성일**: 2025-12-08
**대상 독자**: Backend 개발자, 아키텍트
**목적**: 이벤트 기반 아키텍처 개선 방향 설계 및 기술 검토
