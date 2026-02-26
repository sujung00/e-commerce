-- =====================================================
-- MySQL 데이터베이스 스키마 (DDL)
-- =====================================================
-- 목적: k6 부하테스트를 위한 테이블 생성
-- 실행: docker-compose up 시 자동 실행 (initdb.d 마운트)
-- 순서: 00-schema.sql → 01-seed-test-data.sql
-- Idempotent: CREATE TABLE IF NOT EXISTS 사용
-- =====================================================

-- 데이터베이스 선택
USE hhplus_ecommerce;

-- =====================================================
-- 1. users (사용자)
-- =====================================================
-- 잔액 관리, 주문 주체
-- balance: 충전 금액 (원 단위)
-- version: 낙관적 락 (잔액 동시성 제어)
-- =====================================================
CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_email (email),
    INDEX idx_users_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 2. products (상품)
-- =====================================================
-- 상품 기본 정보
-- total_stock: 모든 옵션의 재고 합계 (계산 필드)
-- status: IN_STOCK | OUT_OF_STOCK | DISCONTINUED
-- version: 낙관적 락 (재고 동시성 제어)
-- =====================================================
CREATE TABLE IF NOT EXISTS products (
    product_id BIGINT NOT NULL AUTO_INCREMENT,
    product_name VARCHAR(255) NOT NULL,
    description TEXT,
    price BIGINT NOT NULL,
    total_stock INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_STOCK',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id),
    INDEX idx_products_status (status),
    INDEX idx_products_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 3. product_options (상품 옵션)
-- =====================================================
-- 옵션별 재고 관리 (색상, 사이즈 등)
-- stock: 옵션별 재고 수량
-- version: 낙관적 락 (재고 동시성 제어)
-- =====================================================
CREATE TABLE IF NOT EXISTS product_options (
    option_id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (option_id),
    INDEX idx_product_options_product_id (product_id),
    INDEX idx_product_options_stock (stock)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 4. carts (장바구니)
-- =====================================================
-- 사용자별 장바구니 (1:1 관계)
-- total_items, total_price: cart_items에서 계산되는 필드
-- UNIQUE(user_id): 사용자당 1개의 장바구니만 허용
-- =====================================================
CREATE TABLE IF NOT EXISTS carts (
    cart_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    total_items INT NOT NULL DEFAULT 0,
    total_price BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (cart_id),
    UNIQUE KEY uk_carts_user_id (user_id),
    INDEX idx_carts_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 5. cart_items (장바구니 항목)
-- =====================================================
-- 장바구니의 라인 항목 (상품 + 옵션 + 수량)
-- subtotal: unit_price * quantity (계산 필드)
-- UNIQUE(cart_id, product_id, option_id): 중복 방지
-- =====================================================
CREATE TABLE IF NOT EXISTS cart_items (
    cart_item_id BIGINT NOT NULL AUTO_INCREMENT,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    option_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    unit_price BIGINT NOT NULL,
    subtotal BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (cart_item_id),
    UNIQUE KEY uk_cart_items_cart_product_option (cart_id, product_id, option_id),
    INDEX idx_cart_items_cart_id (cart_id),
    INDEX idx_cart_items_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 6. coupons (쿠폰)
-- =====================================================
-- 선착순 쿠폰 관리
-- discount_type: FIXED_AMOUNT (정액 할인) | PERCENTAGE (비율 할인)
-- discount_amount: 정액 할인 금액 (discount_type=FIXED_AMOUNT일 때 사용)
-- discount_rate: 비율 할인 (discount_type=PERCENTAGE일 때 사용, 0.1 = 10%)
-- total_quantity: 발급 가능한 총 수량
-- remaining_qty: 남은 수량
-- version: 낙관적 락 (동시성 제어)
-- =====================================================
CREATE TABLE IF NOT EXISTS coupons (
    coupon_id BIGINT NOT NULL AUTO_INCREMENT,
    coupon_name VARCHAR(255) NOT NULL,
    description TEXT,
    discount_type VARCHAR(20) NOT NULL,
    discount_amount BIGINT NOT NULL DEFAULT 0,
    discount_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0000,
    total_quantity INT NOT NULL,
    remaining_qty INT NOT NULL,
    valid_from DATETIME NOT NULL,
    valid_until DATETIME NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (coupon_id),
    INDEX idx_coupons_active_valid (is_active, valid_from, valid_until),
    INDEX idx_coupons_remaining_qty (remaining_qty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 7. user_coupons (사용자 발급 쿠폰)
-- =====================================================
-- 사용자가 발급받은 쿠폰 기록
-- status: UNUSED (미사용) | USED (사용) | EXPIRED (만료) | CANCELLED (취소)
-- UNIQUE(user_id, coupon_id): 중복 발급 방지
-- version: 낙관적 락 (동시성 제어)
-- =====================================================
CREATE TABLE IF NOT EXISTS user_coupons (
    user_coupon_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNUSED',
    version BIGINT DEFAULT 0,
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at DATETIME,
    PRIMARY KEY (user_coupon_id),
    UNIQUE KEY uk_user_coupons_user_coupon (user_id, coupon_id),
    INDEX idx_user_coupons_user_id (user_id),
    INDEX idx_user_coupons_coupon_id (coupon_id),
    INDEX idx_user_coupons_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 8. orders (주문)
-- =====================================================
-- 주문 정보
-- order_status: PENDING | PAID | COMPLETED | FAILED | CANCELLED
-- coupon_discount: 쿠폰 할인 금액
-- subtotal: 상품 금액 합계 (쿠폰 할인 전)
-- final_amount: 최종 결제 금액 (쿠폰 할인 후)
-- =====================================================
CREATE TABLE IF NOT EXISTS orders (
    order_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    order_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    coupon_id BIGINT,
    coupon_discount BIGINT NOT NULL DEFAULT 0,
    subtotal BIGINT NOT NULL,
    final_amount BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    cancelled_at DATETIME,
    PRIMARY KEY (order_id),
    INDEX idx_orders_user_id (user_id),
    INDEX idx_orders_status (order_status),
    INDEX idx_orders_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 9. order_items (주문 항목)
-- =====================================================
-- 주문의 라인 항목 (상품 + 옵션 + 수량)
-- product_name, option_name: 스냅샷 (주문 시점 정보 보존)
-- unit_price: 주문 시점의 단가
-- subtotal: unit_price * quantity
-- =====================================================
CREATE TABLE IF NOT EXISTS order_items (
    order_item_id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    option_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    option_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    unit_price BIGINT NOT NULL,
    subtotal BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (order_item_id),
    INDEX idx_order_items_order_id (order_id),
    INDEX idx_order_items_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 10. outbox (Kafka Outbox 패턴)
-- =====================================================
-- 외부 시스템 전송 메시지 임시 저장
-- status: PENDING | PUBLISHING | PUBLISHED | FAILED | ABANDONED
-- message_type: ORDER_COMPLETED, SHIPPING_REQUEST 등
-- payload: JSON 형태의 메시지 내용
-- retry_count: 재시도 횟수
-- =====================================================
CREATE TABLE IF NOT EXISTS outbox (
    message_id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    payload TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    last_attempt DATETIME,
    sent_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id),
    INDEX idx_outbox_order_id (order_id),
    INDEX idx_outbox_status (status),
    INDEX idx_outbox_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 11. data_platform_events (데이터 플랫폼 전송 이력)
-- =====================================================
-- Kafka Consumer 처리 이력 (중복 방지)
-- UNIQUE(order_id, event_type): 멱등성 보장
-- event_type: ORDER_COMPLETED, ORDER_CANCELLED 등
-- =====================================================
CREATE TABLE IF NOT EXISTS data_platform_events (
    event_id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    user_id BIGINT NOT NULL,
    total_amount BIGINT NOT NULL,
    occurred_at DATETIME NOT NULL,
    processed_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_data_platform_events_order_event (order_id, event_type),
    INDEX idx_data_platform_events_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 스키마 생성 완료 메시지
-- =====================================================
SELECT '✅ 테이블 생성 완료!' AS message;
SELECT
    COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = 'hhplus_ecommerce'
    AND table_type = 'BASE TABLE';