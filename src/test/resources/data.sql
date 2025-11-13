-- ============================================
-- E-Commerce Platform Test Data
-- ============================================
-- 이 스크립트는 application-test.yml에서 자동으로 로드됩니다.
-- 엔티티 간의 외래키 관계를 고려하여 INSERT 순서를 정렬했습니다.
-- 도메인 흐름: 상품 등록 → 장바구니 → 주문 → 결제 → 쿠폰 사용
--
-- 주의: 트랜잭션 내에서 실행되므로 에러 발생 시 전체 롤백됩니다.
-- ============================================

-- ========================================
-- 1. USERS 테이블 (기본 데이터)
-- 10명의 사용자 생성 (균형잡힌 잔액)
-- ========================================

-- User 1: 관리자/테스트 사용자
INSERT INTO users (email, password_hash, name, phone, balance, created_at, updated_at)
VALUES ('admin@example.com', 'hashed_password_1', '관리자', '010-1111-1111', 1000000, NOW(), NOW());

-- User 2: 구매 능력 있는 사용자 (충분한 잔액)
INSERT INTO users (email, password_hash, name, phone, balance, created_at, updated_at)
VALUES ('user2@example.com', 'hashed_password_2', '구매자1', '010-2222-2222', 500000, NOW(), NOW());

-- User 3: 구매 능력 있는 사용자
INSERT INTO users (email, password_hash, name, phone, balance, created_at, updated_at)
VALUES ('user3@example.com', 'hashed_password_3', '구매자2', '010-3333-3333', 300000, NOW(), NOW());

-- User 4: 중간 잔액 사용자
INSERT INTO users (email, password_hash, name, phone, balance, created_at, updated_at)
VALUES ('user4@example.com', 'hashed_password_4', '일반사용자1', '010-4444-4444', 150000, NOW(), NOW());

-- User 5: 낮은 잔액 사용자
INSERT INTO users (email, password_hash, name, phone, balance, created_at, updated_at)
VALUES ('user5@example.com', 'hashed_password_5', '일반사용자2', '010-5555-5555', 50000, NOW(), NOW());

-- User 6: 충분한 잔액 사용자
INSERT INTO users (email, password_hash, name, phone, balance, created_at, updated_at)
VALUES ('user6@example.com', 'hashed_password_6', '구매자3', '010-6666-6666', 250000, NOW(), NOW());

-- User 7: VIP 사용자 (높은 잔액)
INSERT INTO users (email, password_hash, name, phone, balance, created_at, updated_at)
VALUES ('vip@example.com', 'hashed_password_7', 'VIP사용자', '010-7777-7777', 2000000, NOW(), NOW());

-- User 8: 테스트 사용자
INSERT INTO users (email, password_hash, name, phone, balance, created_at, updated_at)
VALUES ('test@example.com', 'hashed_password_8', '테스트사용자', '010-8888-8888', 100000, NOW(), NOW());

-- User 9: 활발한 구매자
INSERT INTO users (email, password_hash, name, phone, balance, created_at, updated_at)
VALUES ('active@example.com', 'hashed_password_9', '활발한구매자', '010-9999-9999', 800000, NOW(), NOW());

-- User 10: 신규 사용자 (낮은 잔액)
INSERT INTO users (email, password_hash, name, phone, balance, created_at, updated_at)
VALUES ('newuser@example.com', 'hashed_password_10', '신규사용자', '010-0000-0000', 10000, NOW(), NOW());

-- ========================================
-- 2. PRODUCTS 테이블 (상품 카탈로그)
-- 12개의 상품 생성 (다양한 카테고리)
-- ========================================

-- Product 1: 티셔츠 (IN_STOCK)
INSERT INTO products (product_name, description, price, total_stock, status, created_at, updated_at)
VALUES ('100% 면 티셔츠', '편안한 착용감의 기본 티셔츠', 29900, 50, 'IN_STOCK', NOW(), NOW());

-- Product 2: 청바지 (IN_STOCK)
INSERT INTO products (product_name, description, price, total_stock, status, created_at, updated_at)
VALUES ('고급 데님 청바지', '스트레치 원단으로 편안함', 79900, 40, 'IN_STOCK', NOW(), NOW());

-- Product 3: 운동화 (IN_STOCK)
INSERT INTO products (product_name, description, price, total_stock, status, created_at, updated_at)
VALUES ('프리미엄 운동화', '최신 기술이 적용된 운동화', 149900, 30, 'IN_STOCK', NOW(), NOW());

-- Product 4: 후드티 (IN_STOCK)
INSERT INTO products (product_name, description, price, total_stock, status, created_at, updated_at)
VALUES ('따뜻한 후드티', '겨울 필수 아이템', 59900, 35, 'IN_STOCK', NOW(), NOW());

-- Product 5: 양말 세트 (IN_STOCK)
INSERT INTO products (product_name, description, price, total_stock, status, created_at, updated_at)
VALUES ('10족 양말 세트', '다양한 색상의 양말 세트', 19900, 100, 'IN_STOCK', NOW(), NOW());

-- Product 6: 모자 (IN_STOCK)
INSERT INTO products (product_name, description, price, total_stock, status, created_at, updated_at)
VALUES ('캐주얼 모자', '자외선 차단 기능', 24900, 60, 'IN_STOCK', NOW(), NOW());

-- Product 7: 선글라스 (IN_STOCK)
INSERT INTO products (product_name, description, price, total_stock, status, created_at, updated_at)
VALUES ('UV 차단 선글라스', '프리미엄 렌즈 적용', 89900, 25, 'IN_STOCK', NOW(), NOW());

-- Product 8: 가방 (IN_STOCK)
INSERT INTO products (product_name, description, price, total_stock, status, created_at, updated_at)
VALUES ('캐주얼 백팩', '대용량 수납 공간', 69900, 45, 'IN_STOCK', NOW(), NOW());

-- Product 9: 시계 (IN_STOCK)
INSERT INTO products (product_name, description, price, total_stock, status, created_at, updated_at)
VALUES ('디지털 시계', '방수 기능 탑재', 49900, 20, 'IN_STOCK', NOW(), NOW());

-- Product 10: 벨트 (IN_STOCK)
INSERT INTO products (product_name, description, price, total_stock, status, created_at, updated_at)
VALUES ('가죽 벨트', '고급 가죽 소재', 39900, 50, 'IN_STOCK', NOW(), NOW());

-- Product 11: 장갑 (IN_STOCK)
INSERT INTO products (product_name, description, price, total_stock, status, created_at, updated_at)
VALUES ('스마트폰 터치 글러브', '겨울 필수 아이템', 34900, 70, 'IN_STOCK', NOW(), NOW());

-- Product 12: 스카프 (IN_STOCK)
INSERT INTO products (product_name, description, price, total_stock, status, created_at, updated_at)
VALUES ('실크 스카프', '우아함과 편안함', 54900, 55, 'IN_STOCK', NOW(), NOW());

-- ========================================
-- 3. PRODUCT_OPTIONS 테이블 (옵션별 재고)
-- 각 상품마다 2~3개의 옵션 생성
-- ========================================

-- Product 1 (티셔츠) 옵션
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (1, 'Black/S', 15, 1, NOW(), NOW());
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (1, 'Black/M', 18, 1, NOW(), NOW());
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (1, 'White/L', 17, 1, NOW(), NOW());

-- Product 2 (청바지) 옵션
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (2, '28 inch', 20, 1, NOW(), NOW());
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (2, '30 inch', 20, 1, NOW(), NOW());

-- Product 3 (운동화) 옵션
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (3, '240mm', 15, 1, NOW(), NOW());
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (3, '250mm', 15, 1, NOW(), NOW());

-- Product 4 (후드티) 옵션
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (4, 'Red/M', 17, 1, NOW(), NOW());
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (4, 'Navy/L', 18, 1, NOW(), NOW());

-- Product 5 (양말 세트) 옵션
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (5, 'Mix Color', 50, 1, NOW(), NOW());
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (5, 'Black Only', 50, 1, NOW(), NOW());

-- Product 6 (모자) 옵션
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (6, 'Black', 30, 1, NOW(), NOW());
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (6, 'White', 30, 1, NOW(), NOW());

-- Product 7 (선글라스) 옵션
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (7, 'Dark Lens', 15, 1, NOW(), NOW());
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (7, 'Light Brown Lens', 10, 1, NOW(), NOW());

-- Product 8 (가방) 옵션
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (8, 'Black', 25, 1, NOW(), NOW());
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (8, 'Navy', 20, 1, NOW(), NOW());

-- Product 9 (시계) 옵션
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (9, 'Black Band', 10, 1, NOW(), NOW());
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (9, 'White Band', 10, 1, NOW(), NOW());

-- Product 10 (벨트) 옵션
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (10, 'Brown/100cm', 25, 1, NOW(), NOW());
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (10, 'Black/110cm', 25, 1, NOW(), NOW());

-- Product 11 (장갑) 옵션
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (11, 'Black', 35, 1, NOW(), NOW());
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (11, 'Grey', 35, 1, NOW(), NOW());

-- Product 12 (스카프) 옵션
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (12, 'Red', 27, 1, NOW(), NOW());
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at)
VALUES (12, 'Navy', 28, 1, NOW(), NOW());

-- ========================================
-- 4. CARTS 테이블 (사용자별 장바구니)
-- 사용자당 정확히 1개의 카트 생성
-- ========================================

INSERT INTO carts (user_id, total_items, total_price, created_at, updated_at)
VALUES (1, 0, 0, NOW(), NOW());

INSERT INTO carts (user_id, total_items, total_price, created_at, updated_at)
VALUES (2, 0, 0, NOW(), NOW());

INSERT INTO carts (user_id, total_items, total_price, created_at, updated_at)
VALUES (3, 0, 0, NOW(), NOW());

INSERT INTO carts (user_id, total_items, total_price, created_at, updated_at)
VALUES (4, 0, 0, NOW(), NOW());

INSERT INTO carts (user_id, total_items, total_price, created_at, updated_at)
VALUES (5, 0, 0, NOW(), NOW());

INSERT INTO carts (user_id, total_items, total_price, created_at, updated_at)
VALUES (6, 0, 0, NOW(), NOW());

INSERT INTO carts (user_id, total_items, total_price, created_at, updated_at)
VALUES (7, 0, 0, NOW(), NOW());

INSERT INTO carts (user_id, total_items, total_price, created_at, updated_at)
VALUES (8, 0, 0, NOW(), NOW());

INSERT INTO carts (user_id, total_items, total_price, created_at, updated_at)
VALUES (9, 0, 0, NOW(), NOW());

INSERT INTO carts (user_id, total_items, total_price, created_at, updated_at)
VALUES (10, 0, 0, NOW(), NOW());

-- ========================================
-- 5. CART_ITEMS 테이블 (장바구니 항목)
-- 일부 사용자의 장바구니에 아이템 추가 (최소 10건 이상)
-- ========================================

-- User 2의 장바구니: 티셔츠 (Black/S) 2개
INSERT INTO cart_items (cart_id, product_id, option_id, quantity, unit_price, subtotal, created_at, updated_at)
VALUES (2, 1, 1, 2, 29900, 59800, NOW(), NOW());

-- User 2의 장바구니: 청바지 (28 inch) 1개
INSERT INTO cart_items (cart_id, product_id, option_id, quantity, unit_price, subtotal, created_at, updated_at)
VALUES (2, 2, 4, 1, 79900, 79900, NOW(), NOW());

-- User 3의 장바구니: 운동화 (240mm) 1개
INSERT INTO cart_items (cart_id, product_id, option_id, quantity, unit_price, subtotal, created_at, updated_at)
VALUES (3, 3, 6, 1, 149900, 149900, NOW(), NOW());

-- User 4의 장바구니: 후드티 (Red/M) 1개
INSERT INTO cart_items (cart_id, product_id, option_id, quantity, unit_price, subtotal, created_at, updated_at)
VALUES (4, 4, 7, 1, 59900, 59900, NOW(), NOW());

-- User 4의 장바구니: 모자 (Black) 1개 추가
INSERT INTO cart_items (cart_id, product_id, option_id, quantity, unit_price, subtotal, created_at, updated_at)
VALUES (4, 6, 11, 1, 24900, 24900, NOW(), NOW());

-- User 6의 장바구니: 양말 세트 (Mix Color) 2개
INSERT INTO cart_items (cart_id, product_id, option_id, quantity, unit_price, subtotal, created_at, updated_at)
VALUES (6, 5, 9, 2, 19900, 39800, NOW(), NOW());

-- User 6의 장바구니: 가방 (Black) 1개 추가
INSERT INTO cart_items (cart_id, product_id, option_id, quantity, unit_price, subtotal, created_at, updated_at)
VALUES (6, 8, 15, 1, 69900, 69900, NOW(), NOW());

-- User 7의 장바구니: 선글라스 (Dark Lens) 1개
INSERT INTO cart_items (cart_id, product_id, option_id, quantity, unit_price, subtotal, created_at, updated_at)
VALUES (7, 7, 13, 1, 89900, 89900, NOW(), NOW());

-- User 8의 장바구니: 벨트 (Brown/100cm) 1개
INSERT INTO cart_items (cart_id, product_id, option_id, quantity, unit_price, subtotal, created_at, updated_at)
VALUES (8, 10, 19, 1, 39900, 39900, NOW(), NOW());

-- User 9의 장바구니: 시계 (Black Band) 1개
INSERT INTO cart_items (cart_id, product_id, option_id, quantity, unit_price, subtotal, created_at, updated_at)
VALUES (9, 9, 19, 1, 49900, 49900, NOW(), NOW());

-- User 9의 장바구니: 장갑 (Black) 1개 추가
INSERT INTO cart_items (cart_id, product_id, option_id, quantity, unit_price, subtotal, created_at, updated_at)
VALUES (9, 11, 21, 2, 34900, 69800, NOW(), NOW());

-- User 10의 장바구니: 스카프 (Red) 1개
INSERT INTO cart_items (cart_id, product_id, option_id, quantity, unit_price, subtotal, created_at, updated_at)
VALUES (10, 12, 23, 1, 54900, 54900, NOW(), NOW());

-- ========================================
-- 6. COUPONS 테이블 (쿠폰 정책)
-- 다양한 할인 유형의 쿠폰 생성
-- ========================================

-- Coupon 1: 정액 할인 10,000원 (선착순 100개)
INSERT INTO coupons (coupon_name, description, discount_type, discount_amount, discount_rate,
                     total_quantity, remaining_qty, valid_from, valid_until, is_active, version, created_at, updated_at)
VALUES ('신규 고객 환영 쿠폰', '새로운 고객을 위한 정액 할인', 'FIXED_AMOUNT', 10000, 0.0,
        100, 85, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 20 DAY), TRUE, 1, NOW(), NOW());

-- Coupon 2: 정액 할인 5,000원 (선착순 200개)
INSERT INTO coupons (coupon_name, description, discount_type, discount_amount, discount_rate,
                     total_quantity, remaining_qty, valid_from, valid_until, is_active, version, created_at, updated_at)
VALUES ('봄 시즌 세일 쿠폰', '봄 시즌 특별 할인', 'FIXED_AMOUNT', 5000, 0.0,
        200, 150, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY), TRUE, 1, NOW(), NOW());

-- Coupon 3: 비율 할인 10% (선착순 300개)
INSERT INTO coupons (coupon_name, description, discount_type, discount_amount, discount_rate,
                     total_quantity, remaining_qty, valid_from, valid_until, is_active, version, created_at, updated_at)
VALUES ('회원 적립금 10% 할인', '회원 포인트로 10% 할인', 'PERCENTAGE', 0, 0.10,
        300, 250, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 60 DAY), TRUE, 1, NOW(), NOW());

-- Coupon 4: 비율 할인 15% (선착순 50개, VIP용)
INSERT INTO coupons (coupon_name, description, discount_type, discount_amount, discount_rate,
                     total_quantity, remaining_qty, valid_from, valid_until, is_active, version, created_at, updated_at)
VALUES ('VIP 고객 15% 할인', 'VIP 회원 전용 할인', 'PERCENTAGE', 0, 0.15,
        50, 48, DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 90 DAY), TRUE, 1, NOW(), NOW());

-- Coupon 5: 정액 할인 20,000원 (한정 쿠폰)
INSERT INTO coupons (coupon_name, description, discount_type, discount_amount, discount_rate,
                     total_quantity, remaining_qty, valid_from, valid_until, is_active, version, created_at, updated_at)
VALUES ('프리미엄 고객 20,000원 할인', '프리미엄 회원 전용', 'FIXED_AMOUNT', 20000, 0.0,
        20, 18, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 45 DAY), TRUE, 1, NOW(), NOW());

-- Coupon 6: 비율 할인 5% (무제한 발급)
INSERT INTO coupons (coupon_name, description, discount_type, discount_amount, discount_rate,
                     total_quantity, remaining_qty, valid_from, valid_until, is_active, version, created_at, updated_at)
VALUES ('일반 할인 5% 쿠폰', '모든 사용자 대상 기본 할인', 'PERCENTAGE', 0, 0.05,
        1000, 900, DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_ADD(NOW(), INTERVAL 100 DAY), TRUE, 1, NOW(), NOW());

-- Coupon 7: 정액 할인 3,000원 (프로모션)
INSERT INTO coupons (coupon_name, description, discount_type, discount_amount, discount_rate,
                     total_quantity, remaining_qty, valid_from, valid_until, is_active, version, created_at, updated_at)
VALUES ('플래시 세일 3,000원 할인', '한정 시간 플래시 세일', 'FIXED_AMOUNT', 3000, 0.0,
        500, 420, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), TRUE, 1, NOW(), NOW());

-- ========================================
-- 7. USER_COUPONS 테이블 (사용자 쿠폰 발급)
-- 사용자에게 쿠폰 발급
-- ========================================

-- User 2에게 쿠폰 1 발급 (신규 고객)
INSERT INTO user_coupons (user_id, coupon_id, status, issued_at, used_at, order_id)
VALUES (2, 1, 'UNUSED', NOW(), NULL, NULL);

-- User 2에게 쿠폰 3 발급 (회원 할인)
INSERT INTO user_coupons (user_id, coupon_id, status, issued_at, used_at, order_id)
VALUES (2, 3, 'UNUSED', NOW(), NULL, NULL);

-- User 3에게 쿠폰 2 발급 (봄 시즌)
INSERT INTO user_coupons (user_id, coupon_id, status, issued_at, used_at, order_id)
VALUES (3, 2, 'UNUSED', NOW(), NULL, NULL);

-- User 4에게 쿠폰 6 발급 (기본 할인)
INSERT INTO user_coupons (user_id, coupon_id, status, issued_at, used_at, order_id)
VALUES (4, 6, 'UNUSED', NOW(), NULL, NULL);

-- User 5에게 쿠폰 7 발급 (플래시 세일)
INSERT INTO user_coupons (user_id, coupon_id, status, issued_at, used_at, order_id)
VALUES (5, 7, 'UNUSED', NOW(), NULL, NULL);

-- User 6에게 쿠폰 3 발급 (회원 할인)
INSERT INTO user_coupons (user_id, coupon_id, status, issued_at, used_at, order_id)
VALUES (6, 3, 'UNUSED', NOW(), NULL, NULL);

-- User 7에게 쿠폰 4 발급 (VIP 할인)
INSERT INTO user_coupons (user_id, coupon_id, status, issued_at, used_at, order_id)
VALUES (7, 4, 'UNUSED', NOW(), NULL, NULL);

-- User 7에게 쿠폰 5 발급 (프리미엄 할인)
INSERT INTO user_coupons (user_id, coupon_id, status, issued_at, used_at, order_id)
VALUES (7, 5, 'UNUSED', NOW(), NULL, NULL);

-- User 9에게 쿠폰 2 발급 (봄 시즌)
INSERT INTO user_coupons (user_id, coupon_id, status, issued_at, used_at, order_id)
VALUES (9, 2, 'UNUSED', NOW(), NULL, NULL);

-- User 10에게 쿠폰 1 발급 (신규 고객)
INSERT INTO user_coupons (user_id, coupon_id, status, issued_at, used_at, order_id)
VALUES (10, 1, 'UNUSED', NOW(), NULL, NULL);

-- ========================================
-- 8. ORDERS 테이블 (주문 기록)
-- 10개의 주문 생성 (다양한 상태와 할인)
-- ========================================

-- Order 1: User 2의 완료된 주문 (쿠폰 할인 적용 X)
INSERT INTO orders (user_id, order_status, coupon_id, coupon_discount, subtotal, final_amount, created_at, updated_at, cancelled_at)
VALUES (2, 'COMPLETED', NULL, 0, 89700, 89700, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), NULL);

-- Order 2: User 3의 완료된 주문 (쿠폰 할인 미적용)
INSERT INTO orders (user_id, order_status, coupon_id, coupon_discount, subtotal, final_amount, created_at, updated_at, cancelled_at)
VALUES (3, 'COMPLETED', NULL, 0, 149900, 149900, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL);

-- Order 3: User 4의 완료된 주문 (쿠폰 할인 적용)
INSERT INTO orders (user_id, order_status, coupon_id, coupon_discount, subtotal, final_amount, created_at, updated_at, cancelled_at)
VALUES (4, 'COMPLETED', 6, 2994, 59900, 56906, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL);

-- Order 4: User 2의 두 번째 주문 (쿠폰 할인 적용)
INSERT INTO orders (user_id, order_status, coupon_id, coupon_discount, subtotal, final_amount, created_at, updated_at, cancelled_at)
VALUES (2, 'COMPLETED', 1, 10000, 29900, 19900, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL);

-- Order 5: User 5의 완료된 주문 (저가격)
INSERT INTO orders (user_id, order_status, coupon_id, coupon_discount, subtotal, final_amount, created_at, updated_at, cancelled_at)
VALUES (5, 'COMPLETED', NULL, 0, 19900, 19900, NOW(), NOW(), NULL);

-- Order 6: User 6의 완료된 주문 (쿠폰 할인 적용)
INSERT INTO orders (user_id, order_status, coupon_id, coupon_discount, subtotal, final_amount, created_at, updated_at, cancelled_at)
VALUES (6, 'COMPLETED', 3, 3990, 39900, 35910, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL);

-- Order 7: User 7의 VIP 주문 (높은 쿠폰 할인)
INSERT INTO orders (user_id, order_status, coupon_id, coupon_discount, subtotal, final_amount, created_at, updated_at, cancelled_at)
VALUES (7, 'COMPLETED', 4, 13485, 89900, 76415, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL);

-- Order 8: User 9의 완료된 주문 (쿠폰 할인 적용)
INSERT INTO orders (user_id, order_status, coupon_id, coupon_discount, subtotal, final_amount, created_at, updated_at, cancelled_at)
VALUES (9, 'COMPLETED', 2, 5000, 49900, 44900, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL);

-- Order 9: User 3의 취소된 주문
INSERT INTO orders (user_id, order_status, coupon_id, coupon_discount, subtotal, final_amount, created_at, updated_at, cancelled_at)
VALUES (3, 'CANCELLED', NULL, 0, 34900, 34900, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY));

-- Order 10: User 10의 새로운 주문 (신규 사용자)
INSERT INTO orders (user_id, order_status, coupon_id, coupon_discount, subtotal, final_amount, created_at, updated_at, cancelled_at)
VALUES (10, 'COMPLETED', 1, 10000, 29900, 19900, NOW(), NOW(), NULL);

-- ========================================
-- 9. ORDER_ITEMS 테이블 (주문 항목 상세)
-- 각 주문의 상세 항목 기록
-- ========================================

-- Order 1 (User 2) 항목: 티셔츠 2개 + 청바지 1개
INSERT INTO order_items (order_id, product_id, option_id, product_name, option_name, quantity, unit_price, subtotal, created_at)
VALUES (1, 1, 1, '100% 면 티셔츠', 'Black/S', 2, 29900, 59800, DATE_SUB(NOW(), INTERVAL 5 DAY));
INSERT INTO order_items (order_id, product_id, option_id, product_name, option_name, quantity, unit_price, subtotal, created_at)
VALUES (1, 2, 4, '고급 데님 청바지', '28 inch', 1, 29900, 29900, DATE_SUB(NOW(), INTERVAL 5 DAY));

-- Order 2 (User 3) 항목: 운동화 1개
INSERT INTO order_items (order_id, product_id, option_id, product_name, option_name, quantity, unit_price, subtotal, created_at)
VALUES (2, 3, 6, '프리미엄 운동화', '240mm', 1, 149900, 149900, DATE_SUB(NOW(), INTERVAL 4 DAY));

-- Order 3 (User 4) 항목: 후드티 1개
INSERT INTO order_items (order_id, product_id, option_id, product_name, option_name, quantity, unit_price, subtotal, created_at)
VALUES (3, 4, 7, '따뜻한 후드티', 'Red/M', 1, 59900, 59900, DATE_SUB(NOW(), INTERVAL 3 DAY));

-- Order 4 (User 2) 항목: 티셔츠 1개
INSERT INTO order_items (order_id, product_id, option_id, product_name, option_name, quantity, unit_price, subtotal, created_at)
VALUES (4, 1, 2, '100% 면 티셔츠', 'Black/M', 1, 29900, 29900, DATE_SUB(NOW(), INTERVAL 2 DAY));

-- Order 5 (User 5) 항목: 양말 세트 1개
INSERT INTO order_items (order_id, product_id, option_id, product_name, option_name, quantity, unit_price, subtotal, created_at)
VALUES (5, 5, 9, '10족 양말 세트', 'Mix Color', 1, 19900, 19900, NOW());

-- Order 6 (User 6) 항목: 모자 2개
INSERT INTO order_items (order_id, product_id, option_id, product_name, option_name, quantity, unit_price, subtotal, created_at)
VALUES (6, 6, 11, '캐주얼 모자', 'Black', 2, 19950, 39900, DATE_SUB(NOW(), INTERVAL 1 DAY));

-- Order 7 (User 7) 항목: 선글라스 1개
INSERT INTO order_items (order_id, product_id, option_id, product_name, option_name, quantity, unit_price, subtotal, created_at)
VALUES (7, 7, 13, 'UV 차단 선글라스', 'Dark Lens', 1, 89900, 89900, DATE_SUB(NOW(), INTERVAL 2 DAY));

-- Order 8 (User 9) 항목: 시계 1개
INSERT INTO order_items (order_id, product_id, option_id, product_name, option_name, quantity, unit_price, subtotal, created_at)
VALUES (8, 9, 19, '디지털 시계', 'Black Band', 1, 49900, 49900, DATE_SUB(NOW(), INTERVAL 1 DAY));

-- Order 9 (User 3, 취소) 항목: 장갑 1개
INSERT INTO order_items (order_id, product_id, option_id, product_name, option_name, quantity, unit_price, subtotal, created_at)
VALUES (9, 11, 21, '스마트폰 터치 글러브', 'Black', 1, 34900, 34900, DATE_SUB(NOW(), INTERVAL 6 DAY));

-- Order 10 (User 10) 항목: 스카프 1개
INSERT INTO order_items (order_id, product_id, option_id, product_name, option_name, quantity, unit_price, subtotal, created_at)
VALUES (10, 12, 23, '실크 스카프', 'Red', 1, 29900, 29900, NOW());

-- ========================================
-- 10. USER_COUPONS (사용된 쿠폰) 업데이트
-- 주문에서 사용된 쿠폰 상태 업데이트
-- ========================================

-- Order 3에서 쿠폰 6 사용 (User 4)
UPDATE user_coupons SET status = 'USED', used_at = DATE_SUB(NOW(), INTERVAL 3 DAY), order_id = 3
WHERE user_id = 4 AND coupon_id = 6;

-- Order 4에서 쿠폰 1 사용 (User 2)
UPDATE user_coupons SET status = 'USED', used_at = DATE_SUB(NOW(), INTERVAL 2 DAY), order_id = 4
WHERE user_id = 2 AND coupon_id = 1;

-- Order 6에서 쿠폰 3 사용 (User 6)
UPDATE user_coupons SET status = 'USED', used_at = DATE_SUB(NOW(), INTERVAL 1 DAY), order_id = 6
WHERE user_id = 6 AND coupon_id = 3;

-- Order 7에서 쿠폰 4 사용 (User 7)
UPDATE user_coupons SET status = 'USED', used_at = DATE_SUB(NOW(), INTERVAL 2 DAY), order_id = 7
WHERE user_id = 7 AND coupon_id = 4;

-- Order 8에서 쿠폰 2 사용 (User 9)
UPDATE user_coupons SET status = 'USED', used_at = DATE_SUB(NOW(), INTERVAL 1 DAY), order_id = 8
WHERE user_id = 9 AND coupon_id = 2;

-- Order 10에서 쿠폰 1 사용 (User 10)
UPDATE user_coupons SET status = 'USED', used_at = NOW(), order_id = 10
WHERE user_id = 10 AND coupon_id = 1;

-- ========================================
-- 11. OUTBOX 테이블 (외부 시스템 전송 메시지)
-- 각 주문에 대한 메시지 큐 생성
-- ========================================

-- Order 1의 메시지
INSERT INTO outbox (order_id, user_id, message_type, status, retry_count, last_attempt, sent_at, created_at)
VALUES (1, 2, 'ORDER_COMPLETED', 'SENT', 0, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY));

-- Order 2의 메시지
INSERT INTO outbox (order_id, user_id, message_type, status, retry_count, last_attempt, sent_at, created_at)
VALUES (2, 3, 'ORDER_COMPLETED', 'SENT', 0, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY));

-- Order 3의 메시지
INSERT INTO outbox (order_id, user_id, message_type, status, retry_count, last_attempt, sent_at, created_at)
VALUES (3, 4, 'ORDER_COMPLETED', 'SENT', 0, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY));

-- Order 4의 메시지
INSERT INTO outbox (order_id, user_id, message_type, status, retry_count, last_attempt, sent_at, created_at)
VALUES (4, 2, 'ORDER_COMPLETED', 'SENT', 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));

-- Order 5의 메시지
INSERT INTO outbox (order_id, user_id, message_type, status, retry_count, last_attempt, sent_at, created_at)
VALUES (5, 5, 'ORDER_COMPLETED', 'SENT', 0, NOW(), NOW(), NOW());

-- Order 6의 메시지
INSERT INTO outbox (order_id, user_id, message_type, status, retry_count, last_attempt, sent_at, created_at)
VALUES (6, 6, 'ORDER_COMPLETED', 'SENT', 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));

-- Order 7의 메시지
INSERT INTO outbox (order_id, user_id, message_type, status, retry_count, last_attempt, sent_at, created_at)
VALUES (7, 7, 'ORDER_COMPLETED', 'SENT', 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));

-- Order 8의 메시지
INSERT INTO outbox (order_id, user_id, message_type, status, retry_count, last_attempt, sent_at, created_at)
VALUES (8, 9, 'ORDER_COMPLETED', 'SENT', 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));

-- Order 9의 메시지 (취소)
INSERT INTO outbox (order_id, user_id, message_type, status, retry_count, last_attempt, sent_at, created_at)
VALUES (9, 3, 'ORDER_CANCELLED', 'SENT', 0, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY));

-- Order 10의 메시지
INSERT INTO outbox (order_id, user_id, message_type, status, retry_count, last_attempt, sent_at, created_at)
VALUES (10, 10, 'ORDER_COMPLETED', 'PENDING', 0, NULL, NULL, NOW());

-- ============================================
-- 데이터 로드 완료
-- ============================================
-- 총 데이터:
-- - Users: 10명
-- - Products: 12개
-- - Product Options: 26개 (옵션)
-- - Carts: 10개 (사용자별 1개)
-- - Cart Items: 12개 (최소 10건 이상)
-- - Coupons: 7개
-- - User Coupons: 10개 (발급)
-- - Orders: 10개 (9 COMPLETED, 1 CANCELLED)
-- - Order Items: 10개
-- - Outbox: 10개 (메시지)
-- ============================================
