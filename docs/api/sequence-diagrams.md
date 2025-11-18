# E-Commerce 플랫폼 시퀀스 다이어그램

## 개요

이 문서는 e-commerce 플랫폼의 주요 비즈니스 흐름을 Mermaid 시퀀스 다이어그램으로 나타냅니다. 각 다이어그램은 참여자(사용자, 프론트엔드, API, 서비스, 데이터베이스, 외부 시스템) 간의 상호작용을 보여줍니다.

---

## 1. 상품 조회 흐름

**관련 엔티티**: users, products, product_options

```mermaid
sequenceDiagram
    participant 사용자
    participant 프론트엔드
    participant ProductController
    participant ProductService
    participant 데이터베이스

    사용자->>프론트엔드: 상품 조회 클릭
    프론트엔드->>ProductController: GET /api/products/{product_id}

    ProductController->>ProductService: getProduct(product_id)

    ProductService->>데이터베이스: SELECT products WHERE product_id=?
    데이터베이스-->>ProductService: 상품 정보 반환

    ProductService->>데이터베이스: SELECT product_options WHERE product_id=?
    데이터베이스-->>ProductService: 옵션 목록 반환<br/>(option_id, name, stock, version)

    ProductService-->>ProductController: 상품 상세 정보<br/>(옵션 포함)

    ProductController-->>프론트엔드: 200 OK<br/>{product_id, name, price,<br/>total_stock, options[]}

    프론트엔드-->>사용자: 상품 상세 페이지 표시

    Note over ProductService,데이터베이스: 캐싱 고려 가능<br/>(TTL: 5분)
```

**비즈니스 로직**:
- 상품 정보는 `products` 테이블에서 조회
- 옵션 정보는 `product_options` 테이블에서 조회
- **옵션별 재고**: 각 옵션의 `stock` 필드 확인
- **상품 총 재고**: 모든 옵션의 재고 합계

---

## 2. 장바구니 담기 흐름

**관련 엔티티**: users, carts, cart_items, products, product_options

```mermaid
sequenceDiagram
    participant 사용자
    participant 프론트엔드
    participant CartController
    participant CartService
    participant ProductService
    participant 데이터베이스

    사용자->>프론트엔드: 상품+옵션+수량 입력 → 장바구니 추가
    프론트엔드->>CartController: POST /api/carts/items<br/>(product_id, option_id, quantity)

    CartController->>CartService: addToCart(user_id, product_id,<br/>option_id, quantity)

    CartService->>ProductService: validateProduct(product_id)
    ProductService->>데이터베이스: SELECT products WHERE product_id=?
    데이터베이스-->>ProductService: 상품 데이터
    ProductService-->>CartService: 검증 완료 ✓

    CartService->>ProductService: validateOption(option_id, product_id)
    ProductService->>데이터베이스: SELECT product_options<br/>WHERE option_id=? AND product_id=?
    데이터베이스-->>ProductService: 옵션 데이터
    ProductService-->>CartService: 검증 완료 ✓

    CartService->>데이터베이스: SELECT carts WHERE user_id=?
    데이터베이스-->>CartService: cart_id 반환

    CartService->>데이터베이스: INSERT cart_items<br/>(cart_id, product_id, option_id,<br/>quantity, unit_price)
    데이터베이스-->>CartService: cart_item_id 반환

    CartService->>데이터베이스: UPDATE carts<br/>SET total_items, total_price
    데이터베이스-->>CartService: 업데이트 완료 ✓

    CartService-->>CartController: 장바구니 아이템 응답
    CartController-->>프론트엔드: 201 Created<br/>{cart_item_id, product_name,<br/>option_name, quantity, unit_price}

    프론트엔드-->>사용자: 장바구니에 추가됨 메시지 표시

    Note over CartService,데이터베이스: ⚠️ 재고 차감 없음!<br/>장바구니는 재고에 영향 주지 않음
```

**비즈니스 로직** (요구사항 2.2.1, 2.1.4):
- 상품과 옵션 검증 (필수)
- **재고 차감 없음**: 장바구니 단계에서는 재고를 차감하지 않음
- **옵션 필수**: cart_items의 option_id는 NOT NULL
- cart_id와 user_id는 1:1 관계

---

## 3. 주문 생성 흐름 (3단계)

**관련 엔티티**: users, products, product_options, orders, order_items, coupons, user_coupons, outbox

```mermaid
sequenceDiagram
    participant 사용자
    participant 프론트엔드
    participant OrderController
    participant OrderService
    participant StockService
    participant PaymentService
    participant 데이터베이스
    participant 외부시스템

    사용자->>프론트엔드: 주문하기 버튼 클릭
    프론트엔드->>OrderController: POST /api/orders<br/>(order_items[], coupon_id)

    OrderController->>OrderService: createOrder(user_id, order_items, coupon_id)

    Note over OrderService: === 1단계: 검증 (읽기 전용) ===

    OrderService->>StockService: validateStock(order_items)
    loop 각 주문 아이템별
        StockService->>데이터베이스: SELECT product_options<br/>WHERE option_id=?
        데이터베이스-->>StockService: option stock, version
        StockService->>StockService: 검증: option_stock >= qty ✓
    end
    StockService-->>OrderService: 재고 충분 ✓

    alt 쿠폰이 있는 경우
        OrderService->>데이터베이스: SELECT coupons WHERE coupon_id=?
        데이터베이스-->>OrderService: 쿠폰 데이터 (discount_type, amount/rate)
        OrderService->>OrderService: 할인액 계산<br/>(discount_type, amount/rate 기반)
        OrderService->>OrderService: 검증: 유효기간, 활성여부, 수량 ✓
        OrderService->>OrderService: final_amount = subtotal - discount
    else 쿠폰이 없는 경우
        OrderService->>OrderService: final_amount = subtotal
    end

    OrderService->>PaymentService: validatePayment(user_id, final_amount)
    PaymentService->>데이터베이스: SELECT users WHERE user_id=?
    데이터베이스-->>PaymentService: user balance
    PaymentService->>PaymentService: 검증: balance >= final_amount ✓
    PaymentService-->>OrderService: 결제 가능 ✓

    Note over OrderService: === 2단계: 원자적 거래 ===

    OrderService->>데이터베이스: BEGIN TRANSACTION

    Note over OrderService,데이터베이스: 다음 모두 성공하거나 모두 롤백

    loop 각 주문 아이템별
        OrderService->>데이터베이스: UPDATE product_options<br/>SET stock = stock - qty<br/>WHERE option_id = ? AND version = current_version
        alt 버전 불일치 (Race Condition)
            데이터베이스-->>OrderService: ❌ 실패 (version mismatch)
            OrderService->>데이터베이스: ROLLBACK
            OrderService-->>OrderController: ERR-004 (Transaction Failed)
            OrderController-->>프론트엔드: 500 Error
            프론트엔드-->>사용자: 주문 실패 (다시 시도하세요)
        else 성공
            데이터베이스-->>OrderService: stock 업데이트 완료, version++
        end
    end

    OrderService->>데이터베이스: UPDATE users<br/>SET balance = balance - final_amount<br/>WHERE user_id = ?
    데이터베이스-->>OrderService: balance 차감 완료 ✓

    alt 쿠폰이 있는 경우
        OrderService->>데이터베이스: UPDATE user_coupons<br/>SET status = 'USED', used_at = NOW()<br/>WHERE user_coupon_id = ?
        데이터베이스-->>OrderService: 쿠폰 사용 완료 ✓
    end

    OrderService->>데이터베이스: INSERT orders<br/>(user_id, order_status='COMPLETED',<br/>subtotal, coupon_discount, final_amount)
    데이터베이스-->>OrderService: order_id 생성 ✓

    loop 각 주문 아이템별
        OrderService->>데이터베이스: INSERT order_items<br/>(order_id, product_id, option_id,<br/>product_name, option_name, qty, unit_price)
        데이터베이스-->>OrderService: order_item_id ✓
    end

    OrderService->>데이터베이스: UPDATE products<br/>SET status = '품절' (if all options stock=0)
    데이터베이스-->>OrderService: 상태 업데이트 완료 ✓

    OrderService->>데이터베이스: COMMIT
    데이터베이스-->>OrderService: 트랜잭션 커밋 완료 ✓

    Note over OrderService: === 3단계: 외부 전송 대기열 ===

    OrderService->>데이터베이스: INSERT outbox<br/>(order_id, message_type='SHIPPING_REQUEST',<br/>status='PENDING')
    데이터베이스-->>OrderService: outbox 기록 완료 ✓

    OrderService-->>OrderController: 주문 완료 응답
    OrderController-->>프론트엔드: 201 Created<br/>{order_id, order_status='COMPLETED',<br/>final_amount, ...}
    프론트엔드-->>사용자: 주문 완료 페이지 표시

    Note over 데이터베이스,외부시스템: ✓ 비동기: 별도 프로세스가<br/>outbox를 처리하여 외부시스템 전송<br/>(주문 완료 흐름과 독립적)
```

**비즈니스 로직** (요구사항 2.2.2, 2.2.3, 2.4):
- **1단계 (검증)**: 모든 읽기만 수행, 데이터 변경 없음
  - 각 옵션의 재고 확인 (옵션 ID 기준)
  - 사용자 잔액 확인
  - 쿠폰 유효성 확인
- **2단계 (원자적 거래)**: 모두 성공하거나 모두 롤백
  - 각 옵션의 재고 차감 (낙관적 락)
  - 사용자 잔액 차감
  - 주문 및 주문 항목 생성
  - 쿠폰 사용 처리
- **3단계 (외부 전송)**: 주문 완료 후 비동기 처리
  - 트랜잭션과 독립적
  - outbox에 메시지 저장
  - 별도 프로세스가 처리

---

## 4. 동시 주문 처리 (Race Condition 방지)

**관련 엔티티**: product_options (version 필드)

```mermaid
sequenceDiagram
    participant 사용자A
    participant 사용자B
    participant OrderService
    participant 데이터베이스

    Note over 사용자A,데이터베이스: 상황: 같은 옵션 재고 = 1개

    사용자A->>OrderService: 주문 요청 (option_id=101, qty=1)
    사용자B->>OrderService: 주문 요청 (option_id=101, qty=1)

    par 병렬 처리
        OrderService->>데이터베이스: SELECT product_options<br/>WHERE option_id=101
        데이터베이스-->>OrderService: stock=1, version=10
        OrderService->>OrderService: 검증: 1 >= 1 ✓
    and
        OrderService->>데이터베이스: SELECT product_options<br/>WHERE option_id=101
        데이터베이스-->>OrderService: stock=1, version=10
        OrderService->>OrderService: 검증: 1 >= 1 ✓
    end

    Note over OrderService,데이터베이스: === 2단계: 원자적 거래 ===

    par 사용자A의 거래
        OrderService->>데이터베이스: BEGIN TRANSACTION
        OrderService->>데이터베이스: UPDATE product_options<br/>SET stock=0, version=11<br/>WHERE option_id=101 AND version=10
        데이터베이스-->>OrderService: ✓ 업데이트 성공<br/>(1행 영향)
        OrderService->>데이터베이스: INSERT orders, order_items
        데이터베이스-->>OrderService: ✓ 주문 생성
        OrderService->>데이터베이스: COMMIT
        데이터베이스-->>OrderService: ✓ 커밋 완료
        OrderService-->>사용자A: 201 Created<br/>order_status='COMPLETED'
    and 사용자B의 거래
        OrderService->>데이터베이스: BEGIN TRANSACTION
        OrderService->>데이터베이스: UPDATE product_options<br/>SET stock=0, version=11<br/>WHERE option_id=101 AND version=10
        데이터베이스-->>OrderService: ❌ 실패<br/>(0행 영향, version 현재=11)
        OrderService->>데이터베이스: ROLLBACK
        데이터베이스-->>OrderService: ✓ 롤백 완료
        OrderService-->>사용자B: 500 Error<br/>ERR-004 (Transaction Failed)
    end

    사용자B->>OrderService: 재시도 (option_id=101, qty=1)
    OrderService->>데이터베이스: SELECT product_options WHERE option_id=101
    데이터베이스-->>OrderService: stock=0, version=11
    OrderService->>OrderService: 검증: 0 >= 1 ❌
    OrderService-->>사용자B: 400 Bad Request<br/>ERR-001 (블랙 / M의 재고가 부족합니다)
```

**동시성 제어** (요구사항 2.1.2, 3.2):
- **낙관적 락**: version 필드를 이용한 동시성 제어
- 두 사용자가 동시에 같은 상품 주문 가능
- 첫 번째 사용자: version 일치 → 성공, version 증가
- 두 번째 사용자: version 불일치 → 실패, 롤백

---

## 5. 쿠폰 발급 흐름 (선착순)

**관련 엔티티**: coupons, user_coupons

```mermaid
sequenceDiagram
    participant 사용자A
    participant 사용자B
    participant CouponController
    participant CouponService
    participant 데이터베이스

    Note over 사용자A,데이터베이스: 상황: 쿠폰 remaining_qty=1

    par 병렬 발급 요청
        사용자A->>CouponController: POST /api/coupons/issue<br/>(coupon_id=1)
    and
        사용자B->>CouponController: POST /api/coupons/issue<br/>(coupon_id=1)
    end

    par 사용자A의 발급
        CouponController->>CouponService: issueCoupon(user_id=100, coupon_id=1)

        CouponService->>데이터베이스: SELECT coupons<br/>WHERE coupon_id=1<br/>FOR UPDATE (비관적 락)
        데이터베이스-->>CouponService: coupon 데이터<br/>(remaining_qty=1, version=5)

        CouponService->>CouponService: 검증:<br/>is_active=true ✓<br/>valid_from <= NOW <= valid_until ✓<br/>remaining_qty > 0 ✓

        CouponService->>데이터베이스: UPDATE coupons<br/>SET remaining_qty=0, version=6<br/>WHERE coupon_id=1
        데이터베이스-->>CouponService: ✓ 원자적 감소 완료

        CouponService->>데이터베이스: INSERT user_coupons<br/>(user_id=100, coupon_id=1,<br/>status='ACTIVE', issued_at=NOW())
        데이터베이스-->>CouponService: user_coupon_id 생성 ✓

        CouponService-->>CouponController: 쿠폰 발급 완료
        CouponController-->>사용자A: 201 Created<br/>{user_coupon_id, status='ACTIVE'}
    and 사용자B의 발급
        CouponController->>CouponService: issueCoupon(user_id=101, coupon_id=1)

        CouponService->>데이터베이스: SELECT coupons<br/>WHERE coupon_id=1<br/>FOR UPDATE (비관적 락)
        Note over 데이터베이스: ⏳ 대기: A의 락 해제까지 대기

        Note over 데이터베이스: A의 UPDATE 완료, 락 해제
        데이터베이스-->>CouponService: coupon 데이터<br/>(remaining_qty=0, version=6)

        CouponService->>CouponService: 검증:<br/>remaining_qty > 0 ❌

        CouponService-->>CouponController: ERR-003<br/>(Coupon Exhausted)
        CouponController-->>사용자B: 400 Bad Request<br/>ERR-003 (쿠폰이 모두 소진되었습니다)
    end
```

**쿠폰 발급 로직** (요구사항 2.3.1, 3.2):
- **비관적 락**: SELECT ... FOR UPDATE로 다른 요청 차단
- 재고 > 0 검증
- **원자적 감소**: remaining_qty를 한 번에 1 감소
- **UNIQUE 제약**: UNIQUE(user_id, coupon_id)로 중복 발급 방지
- 동시 요청 시 한 명만 성공, 나머지는 ERR-003

---

## 6. 주문 조회 흐름

**관련 엔티티**: users, orders, order_items, products, product_options

```mermaid
sequenceDiagram
    participant 사용자
    participant 프론트엔드
    participant OrderController
    participant OrderService
    participant 데이터베이스

    사용자->>프론트엔드: 주문 상세 조회 클릭
    프론트엔드->>OrderController: GET /api/orders/{order_id}

    OrderController->>OrderService: getOrder(order_id, user_id)

    OrderService->>데이터베이스: SELECT orders WHERE order_id=? AND user_id=?
    데이터베이스-->>OrderService: 주문 정보

    OrderService->>데이터베이스: SELECT order_items WHERE order_id=?
    데이터베이스-->>OrderService: 주문 항목 목록<br/>(product_id, option_id, qty, unit_price)

    OrderService-->>OrderController: 주문 상세 정보
    OrderController-->>프론트엔드: 200 OK<br/>{order_id, order_status,<br/>subtotal, final_amount,<br/>order_items[], created_at}

    프론트엔드-->>사용자: 주문 상세 페이지 표시

    Note over OrderService,데이터베이스: 참고: order_items는<br/>product_name, option_name<br/>스냅샷을 포함 (감사 추적)
```

---

## 7. 쿠폰 적용 주문 흐름

**관련 엔티티**: users, products, product_options, orders, order_items, coupons, user_coupons

```mermaid
sequenceDiagram
    participant 사용자
    participant 프론트엔드
    participant OrderController
    participant OrderService
    participant 데이터베이스

    사용자->>프론트엔드: 주문 아이템 + 쿠폰 선택 → 결제
    프론트엔드->>OrderController: POST /api/orders<br/>{order_items[], coupon_id=1}

    OrderController->>OrderService: createOrder(user_id, items, coupon_id=1)

    Note over OrderService: === 검증 단계 ===

    OrderService->>데이터베이스: SELECT coupons WHERE coupon_id=1
    데이터베이스-->>OrderService: coupon (discount_type, discount_amount/rate)

    OrderService->>데이터베이스: SELECT user_coupons<br/>WHERE user_id=? AND coupon_id=1
    데이터베이스-->>OrderService: user_coupon (status=ACTIVE)

    OrderService->>OrderService: 검증:<br/>- 쿠폰 존재 ✓<br/>- 사용자가 보유 ✓<br/>- 유효기간 내 ✓<br/>- 상태 = ACTIVE ✓

    OrderService->>OrderService: 할인 계산:<br/>할인액 = 계산(discount_type, amount/rate)<br/>결제금액 = 상품합계 - 할인액

    OrderService->>데이터베이스: SELECT users WHERE user_id=?
    데이터베이스-->>OrderService: user balance
    OrderService->>OrderService: 검증: balance >= 결제금액 ✓

    Note over OrderService: === 원자적 거래 ===

    OrderService->>데이터베이스: BEGIN TRANSACTION

    OrderService->>데이터베이스: UPDATE product_options<br/>SET stock = stock - qty<br/>WHERE option_id = ? AND version = ?
    데이터베이스-->>OrderService: ✓

    OrderService->>데이터베이스: UPDATE users<br/>SET balance = balance - 결제금액<br/>WHERE user_id = ?
    데이터베이스-->>OrderService: ✓

    OrderService->>데이터베이스: UPDATE user_coupons<br/>SET status='USED', used_at=NOW()<br/>WHERE user_coupon_id = ?
    데이터베이스-->>OrderService: ✓ 쿠폰 사용 처리

    OrderService->>데이터베이스: INSERT orders<br/>(coupon_id=1, coupon_discount=할인액,<br/>subtotal=상품합계, final_amount=결제금액)
    데이터베이스-->>OrderService: order_id ✓

    OrderService->>데이터베이스: INSERT order_items (...)
    데이터베이스-->>OrderService: ✓

    OrderService->>데이터베이스: COMMIT
    데이터베이스-->>OrderService: ✓

    OrderService->>데이터베이스: INSERT outbox (...)
    데이터베이스-->>OrderService: ✓

    OrderService-->>OrderController: 주문 완료
    OrderController-->>프론트엔드: 201 Created<br/>{order_id, subtotal, coupon_discount,<br/>final_amount, order_status='COMPLETED'}

    프론트엔드-->>사용자: 주문 완료 페이지<br/>(할인액 표시)
```

**쿠폰 적용 로직** (요구사항 2.2.4):
- **결제 금액 계산**: (상품 합계 - 쿠폰 할인액)
- **원자적 처리**: 쿠폰 사용 상태도 거래에 포함
- **쿠폰 상태 변경**: ACTIVE → USED
- 모든 작업이 성공하거나 모두 롤백

---

## 8. 외부 시스템 전송 (비동기)

**관련 엔티티**: outbox, orders

```mermaid
sequenceDiagram
    participant OrderService
    participant 데이터베이스
    participant OutboxJob
    participant 외부시스템

    OrderService->>데이터베이스: INSERT outbox<br/>(order_id, message_type='SHIPPING_REQUEST',<br/>status='PENDING', retry_count=0)
    데이터베이스-->>OrderService: ✓ 저장 완료

    Note over OrderService: ✓ 주문 완료<br/>(외부 전송 완료 대기 안함)

    Note over 데이터베이스,OutboxJob: === 5분마다 실행 ===

    OutboxJob->>데이터베이스: SELECT outbox WHERE status='PENDING'<br/>AND last_attempt < NOW()-5min
    데이터베이스-->>OutboxJob: pending 메시지 목록

    loop 각 메시지별
        OutboxJob->>데이터베이스: SELECT orders WHERE order_id=?
        데이터베이스-->>OutboxJob: 주문 정보

        alt 전송 성공
            OutboxJob->>외부시스템: POST /api/shipping<br/>{order_id, user_id, items, total_amount}
            외부시스템-->>OutboxJob: 200 OK

            OutboxJob->>데이터베이스: UPDATE outbox<br/>SET status='SENT', sent_at=NOW()<br/>WHERE message_id = ?
            데이터베이스-->>OutboxJob: ✓ 업데이트
        else 전송 실패 (재시도 가능)
            OutboxJob->>외부시스템: POST /api/shipping (...)
            외부시스템-->>OutboxJob: ❌ 500 Error

            OutboxJob->>데이터베이스: UPDATE outbox<br/>SET retry_count = retry_count + 1,<br/>last_attempt = NOW()<br/>WHERE message_id = ?
            데이터베이스-->>OutboxJob: ✓

            alt retry_count < 5
                Note over OutboxJob: ⏳ 다음 실행까지 대기<br/>(지수 백오프: 1, 2, 4, 8, 16분)
            else retry_count >= 5
                OutboxJob->>데이터베이스: UPDATE outbox<br/>SET status='FAILED'
                데이터베이스-->>OutboxJob: ✓ 최종 실패
            end
        end
    end

    Note over 데이터베이스: 참고: 주문 상태는 항상 'COMPLETED'<br/>외부 전송 결과와 무관
```

**외부 전송 로직** (요구사항 2.4):
- **비동기 처리**: 주문 완료와 독립적
- **Outbox 패턴**: 신뢰성 있는 전송 보장
- **재시도 전략**: 지수 백오프 (1, 2, 4, 8, 16분), 최대 5회
- **실패 처리**: 전송 실패해도 주문은 COMPLETED 상태 유지

---

## 9. 데이터 일관성 검증 (정기적)

**관련 엔티티**: products, product_options

```mermaid
sequenceDiagram
    participant 배치작업
    participant 데이터베이스

    Note over 배치작업,데이터베이스: === 매일 1회 실행 (예: 새벽 2시) ===

    배치작업->>데이터베이스: SELECT products WHERE status != '삭제'
    데이터베이스-->>배치작업: 상품 목록

    loop 각 상품별
        배치작업->>데이터베이스: SELECT SUM(stock) FROM product_options<br/>WHERE product_id = ?
        데이터베이스-->>배치작업: 계산된 총 재고

        배치작업->>데이터베이스: SELECT total_stock FROM products<br/>WHERE product_id = ?
        데이터베이스-->>배치작업: 저장된 총 재고

        alt 일치함
            배치작업->>배치작업: ✓ 일관성 확인
        else 불일치
            배치작업->>배치작업: ⚠️ 불일치 감지!

            배치작업->>데이터베이스: UPDATE products<br/>SET total_stock = 계산된값<br/>WHERE product_id = ?
            데이터베이스-->>배치작업: ✓ 수정 완료

            배치작업->>배치작업: 알림: 관리자에게 보고
        end
    end

    Note over 배치작업: ✓ 검증 완료
```

**데이터 일관성** (요구사항 3.3, BR-04-2):
- **일관성 검증**: products.total_stock = SUM(product_options.stock)
- **정기적 검증**: 일일 배치 작업으로 검증
- **자동 수정**: 불일치 시 자동으로 재계산하여 수정
- **감사 로그**: 불일치 발견 시 관리자 알림

---

## 10. 에러 처리 및 응답 코드

### 에러 코드 정의

| 에러 코드 | HTTP 상태 | 메시지 | 상황 |
|----------|-----------|--------|------|
| ERR-001 | 400 | "[옵션명]의 재고가 부족합니다" | 옵션 재고 부족 |
| ERR-002 | 400 | "잔액이 부족합니다" | 사용자 잔액 부족 |
| ERR-003 | 400 | "유효하지 않은 쿠폰입니다" | 쿠폰 검증 실패 또는 소진 |
| ERR-004 | 500 | "주문 생성에 실패했습니다" | 거래 실패 (버전 불일치) |

### 요청/응답 예시

**성공 응답 (201 Created)**:
```json
{
  "order_id": 5001,
  "order_status": "COMPLETED",
  "subtotal": 59800,
  "coupon_discount": 0,
  "final_amount": 59800,
  "created_at": "2025-10-29T12:45:00Z"
}
```

**에러 응답 (400 Bad Request)**:
```json
{
  "error_code": "ERR-001",
  "error_message": "블랙 / M의 재고가 부족합니다",
  "timestamp": "2025-10-29T12:45:00Z"
}
```

---

## 참고사항

### 동시성 제어 전략

| 작업 | 제어 방식 | 필드 |
|------|---------|------|
| 주문 시 재고 차감 | 낙관적 락 | product_options.version |
| 쿠폰 발급 | 비관적 락 | SELECT ... FOR UPDATE |
| 사용자 잔액 차감 | 트랜잭션 격리 | 읽기-수정-쓰기 원자성 보장 |

### 트랜잭션 격리 수준

- **최소 수준**: READ_COMMITTED 이상
- **권장 수준**: REPEATABLE_READ 또는 SERIALIZABLE
- **목적**: 더티 리드, 비반복 읽기, 팬텀 읽기 방지

### 성능 고려사항

- **캐싱**: 상품 정보 조회 (TTL: 5분)
- **인덱싱**: product_id, user_id, option_id, coupon_id에 인덱스 필수
- **배치 처리**: 외부 전송, 데이터 검증 등 비동기 작업

---

## 11. 주문 취소 및 쿠폰 복구 흐름

**관련 엔티티**: orders, order_items, product_options, users, user_coupons, coupons

**설명**: 사용자가 주문을 취소할 때 모든 자원(주문 상태, 재고, 잔액, 쿠폰)을 원자적으로 복구하는 흐름입니다. 특히 쿠폰 복구는 다른 활성 주문에서 사용 중인지 확인 후 결정됩니다.

```mermaid
sequenceDiagram
    participant 사용자
    participant 프론트엔드
    participant OrderController
    participant OrderService
    participant OrderCancelTransactionService
    participant OrderRepository
    participant UserCouponRepository
    participant 데이터베이스

    사용자->>프론트엔드: 주문 취소 요청
    프론트엔드->>OrderController: POST /api/orders/{order_id}/cancel

    OrderController->>OrderService: cancelOrder(orderId, userId)

    OrderService->>데이터베이스: SELECT orders WHERE order_id=?
    데이터베이스-->>OrderService: 주문 정보 반환<br/>(status, coupon_id, final_amount)

    OrderService->>OrderCancelTransactionService: executeTransactionalCancel<br/>(orderId, userId, order)

    OrderCancelTransactionService->>데이터베이스: UPDATE orders<br/>SET order_status='CANCELLED',<br/>cancelled_at=NOW()

    OrderCancelTransactionService->>데이터베이스: SELECT order_items WHERE order_id=?
    데이터베이스-->>OrderCancelTransactionService: 주문 항목 목록 반환<br/>(product_id, option_id, quantity)

    loop 각 주문 항목별로
        OrderCancelTransactionService->>데이터베이스: SELECT product_options<br/>WHERE option_id=?
        데이터베이스-->>OrderCancelTransactionService: 옵션 정보 반환

        OrderCancelTransactionService->>데이터베이스: UPDATE product_options<br/>SET stock+=quantity, version+=1<br/>(낙관적 락)
    end

    OrderCancelTransactionService->>데이터베이스: SELECT users WHERE user_id=?
    데이터베이스-->>OrderCancelTransactionService: 사용자 정보 반환

    OrderCancelTransactionService->>데이터베이스: UPDATE users<br/>SET balance+=final_amount

    alt 주문에 쿠폰이 적용된 경우 (coupon_id != null)
        OrderCancelTransactionService->>OrderRepository: existsActiveByCouponId(couponId)
        OrderRepository->>데이터베이스: SELECT COUNT(*) FROM orders<br/>WHERE coupon_id=? AND order_status='COMPLETED'
        데이터베이스-->>OrderRepository: 활성 주문 수 반환

        alt 쿠폰이 다른 활성 주문에서 사용 중이 아닌 경우
            OrderCancelTransactionService->>UserCouponRepository: findByUserIdAndCouponId<br/>(userId, couponId)
            UserCouponRepository->>데이터베이스: SELECT user_coupons<br/>WHERE user_id=? AND coupon_id=?
            데이터베이스-->>UserCouponRepository: UserCoupon 반환

            alt UserCoupon이 존재하는 경우
                OrderCancelTransactionService->>데이터베이스: UPDATE user_coupons<br/>SET status='UNUSED',<br/>used_at=NULL
                Note over OrderCancelTransactionService,데이터베이스: 쿠폰 상태를 UNUSED로 복구<br/>(감사 추적: log.info 기록)
            else UserCoupon이 없는 경우
                Note over OrderCancelTransactionService,데이터베이스: 경고 로깅만 수행<br/>(이미 삭제되었을 수 있음)
            end
        else 쿠폰이 다른 활성 주문에서 사용 중인 경우
            Note over OrderCancelTransactionService,OrderRepository: 쿠폰 복구 스킵<br/>(다른 주문의 쿠폰이므로)<br/>(log.info 기록)
        end
    end

    OrderCancelTransactionService->>데이터베이스: INSERT into outbox<br/>(order_id, message_type='ORDER_CANCELLED',<br/>status='PENDING')

    OrderCancelTransactionService-->>OrderService: 취소 응답<br/>{orderId, status='CANCELLED',<br/>refundAmount, restoredItems[],<br/>cancelledAt}

    OrderService-->>OrderController: 취소 결과

    OrderController-->>프론트엔드: 200 OK

    프론트엔드-->>사용자: 주문 취소 완료 메시지
```

**비즈니스 로직** (변경: 2025-11-18):

**1단계: 주문 상태 변경**
- 주문 상태를 COMPLETED → CANCELLED로 변경
- cancelled_at 필드에 현재 시간 설정

**2단계: 재고 복구**
- 주문의 모든 항목(order_items)을 조회
- 각 상품 옵션의 재고를 원래 수량만큼 복구
- 낙관적 락(version 증가)으로 동시성 제어

**3단계: 사용자 잔액 복구**
- 최종 결제액(final_amount)을 사용자 잔액에 반환

**4단계: 쿠폰 복구** ⭐ **2025-11-18 신규**
- **(1) 쿠폰 사용 중 여부 확인**: `OrderRepository.existsActiveByCouponId(couponId)` 호출
  - 해당 쿠폰이 다른 **활성(COMPLETED) 주문**에서 사용 중인지 확인
  - SQL: `SELECT COUNT(*) FROM orders WHERE coupon_id=? AND order_status='COMPLETED'`

- **(2) UserCoupon 조회**: 쿠폰이 다른 주문에서 사용 중이 아닐 때만
  - `UserCouponRepository.findByUserIdAndCouponId(userId, couponId)` 호출
  - UserCoupon 엔티티 존재 여부 확인

- **(3) 상태 복구**: UserCoupon이 존재할 경우
  - `UserCoupon.status` = `UNUSED`로 변경
  - `used_at` 필드를 NULL로 초기화
  - SLF4J 로깅: `log.info()` (감사 추적)

**변경 사항 (2025-11-18)**:
- **이전**: 쿠폰 사용 여부를 `user_coupons.order_id` 칼럼으로 추적
- **현재**: 쿠폰 사용 여부를 `orders.coupon_id` 존재 여부로 추적
  - `user_coupons` 테이블은 "쿠폰 보유 상태"(UNUSED/USED/EXPIRED/CANCELLED)만 관리
  - 쿠폰 복구 판단: 다른 활성 주문에서 사용 중인지 확인 후 결정

**5단계: Outbox 메시지 생성**
- 주문 취소 이벤트를 outbox에 저장
- 비동기 배치가 외부 시스템에 전송

**에러 처리**:
- 낙관적 락 실패 → 버전 불일치 → 트랜잭션 롤백
- UserCoupon 미발견 → 경고 로깅 후 계속 진행 (쿠폰이 이미 삭제되었을 가능성)
- 모든 작업은 `@Transactional` 범위 내에서 원자적으로 처리

### 요청/응답 예시

**요청**:
```http
POST /api/orders/{order_id}/cancel
Authorization: Bearer {token}
Content-Type: application/json

{}
```

**성공 응답 (200 OK)**:
```json
{
  "order_id": 5001,
  "order_status": "CANCELLED",
  "refund_amount": 59800,
  "cancelled_at": "2025-11-18T14:30:00Z",
  "restored_items": [
    {
      "order_item_id": 1,
      "product_id": 101,
      "product_name": "나이키 에어맥스",
      "option_id": 201,
      "option_name": "Black/M",
      "quantity": 2,
      "restored_stock": 15
    }
  ]
}
```

**에러 응답 (400 Bad Request)**:
```json
{
  "error_code": "CANCEL-001",
  "error_message": "이미 취소된 주문입니다",
  "timestamp": "2025-11-18T14:30:00Z"
}
```
