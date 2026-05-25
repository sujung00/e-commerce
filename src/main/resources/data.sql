-- ============================================================
-- 캐시 히트율 측정용 테스트 데이터
-- 목적: userCouponsCache / cartCache / inventoryCache 실측
--
-- 삽입 순서 (FK 참조 관계):
--   users → products → product_options
--        → coupons → user_coupons
--        → carts → cart_items
-- ============================================================

-- ── 1. 사용자 3명 ─────────────────────────────────────────────
INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('testuser1@cache.test', 'hashed_pw_1', '캐시테스터1', '010-1001-1001', 500000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('testuser2@cache.test', 'hashed_pw_2', '캐시테스터2', '010-1001-1002', 300000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('testuser3@cache.test', 'hashed_pw_3', '캐시테스터3', '010-1001-1003', 200000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ── 2. 상품 3개 (inventoryCache 측정용) ───────────────────────
INSERT INTO products (product_name, description, price, total_stock, status, created_at, updated_at) VALUES
('테스트상품A', 'inventoryCache 측정용 상품 1', 10000, 100, 'IN_STOCK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO products (product_name, description, price, total_stock, status, created_at, updated_at) VALUES
('테스트상품B', 'inventoryCache 측정용 상품 2', 20000, 50, 'IN_STOCK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO products (product_name, description, price, total_stock, status, created_at, updated_at) VALUES
('테스트상품C', 'inventoryCache 측정용 상품 3', 30000, 30, 'IN_STOCK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ── 3. 상품 옵션 (product_id 참조 / inventoryCache 필수) ──────
INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at) VALUES
(1, '기본옵션-A', 50, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at) VALUES
(2, '기본옵션-B', 30, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO product_options (product_id, name, stock, version, created_at, updated_at) VALUES
(3, '기본옵션-C', 20, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ── 4. 쿠폰 (user_coupons 에서 참조) ─────────────────────────
-- valid_from/until: 2026-01-01 ~ 2026-12-31 (현재 날짜 포함)
INSERT INTO coupons (coupon_name, description, discount_type, discount_amount, discount_rate,
                     total_quantity, remaining_qty, valid_from, valid_until,
                     is_active, version, created_at, updated_at) VALUES
('히트율측정쿠폰A', 'userCouponsCache 측정용', 'FIXED_AMOUNT', 5000, 0.00,
 100, 97, '2026-01-01 00:00:00', '2026-12-31 23:59:59',
 TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ── 5. 사용자 쿠폰 발급 (userCouponsCache 핵심 데이터) ────────
-- unless = "#result.isEmpty()" → 결과가 비어 있으면 캐시 안 됨
-- UNUSED 상태로 3명 각각 발급 → 쿼리 결과 non-empty 보장
INSERT INTO user_coupons (user_id, coupon_id, status, version, issued_at) VALUES
(1, 1, 'UNUSED', 0, CURRENT_TIMESTAMP);

INSERT INTO user_coupons (user_id, coupon_id, status, version, issued_at) VALUES
(2, 1, 'UNUSED', 0, CURRENT_TIMESTAMP);

INSERT INTO user_coupons (user_id, coupon_id, status, version, issued_at) VALUES
(3, 1, 'UNUSED', 0, CURRENT_TIMESTAMP);

-- ── 6. 장바구니 (user_id=1, cartCache 측정용) ─────────────────
-- CartService.findOrCreateByUserId()가 없으면 자동 생성하지만
-- 미리 삽입하여 cart_items까지 포함된 non-trivial 결과 보장
INSERT INTO carts (user_id, total_items, total_price, created_at, updated_at) VALUES
(1, 2, 30000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ── 7. 장바구니 항목 (cart_id=1) ──────────────────────────────
-- CartService.getProductName()은 DB 조회 없이 switch 로 처리하므로
-- product_id / option_id 는 참조 무결성 검증 없이 저장 가능
INSERT INTO cart_items (cart_id, product_id, option_id, quantity, unit_price, subtotal,
                        created_at, updated_at) VALUES
(1, 1, 1, 1, 10000, 10000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO cart_items (cart_id, product_id, option_id, quantity, unit_price, subtotal,
                        created_at, updated_at) VALUES
(1, 2, 2, 1, 20000, 20000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============================================================
-- Consumer TPS 측정용 테스트 데이터 (추가분)
-- 목적: Redis Queue / Kafka Consumer 처리 TPS 측정
-- 쿠폰2: total_quantity=500, 기존 발급 없음 → 200건 발급 가능
-- 사용자4~203: perf{n}@test.com, 쿠폰2 미발급 상태
-- ============================================================

-- ── 8. Consumer TPS 측정용 쿠폰 (coupon_id=2) ───────────────
INSERT INTO coupons (coupon_name, description, discount_type, discount_amount, discount_rate,
                     total_quantity, remaining_qty, valid_from, valid_until,
                     is_active, version, created_at, updated_at) VALUES
('컨슈머TPS측정쿠폰B', 'Redis Queue / Kafka Consumer TPS 측정용 (발급 없음)', 'FIXED_AMOUNT', 3000, 0.00,
 500, 500, '2026-01-01 00:00:00', '2026-12-31 23:59:59',
 TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ── 9. Consumer TPS 측정용 사용자 200명 (user_id 4~203) ─────
-- 이메일 UNIQUE, 전화번호 non-unique → 동일 전화번호 허용
INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf4@test.com', 'pw', '성능테스터4', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf5@test.com', 'pw', '성능테스터5', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf6@test.com', 'pw', '성능테스터6', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf7@test.com', 'pw', '성능테스터7', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf8@test.com', 'pw', '성능테스터8', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf9@test.com', 'pw', '성능테스터9', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf10@test.com', 'pw', '성능테스터10', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf11@test.com', 'pw', '성능테스터11', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf12@test.com', 'pw', '성능테스터12', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf13@test.com', 'pw', '성능테스터13', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf14@test.com', 'pw', '성능테스터14', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf15@test.com', 'pw', '성능테스터15', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf16@test.com', 'pw', '성능테스터16', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf17@test.com', 'pw', '성능테스터17', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf18@test.com', 'pw', '성능테스터18', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf19@test.com', 'pw', '성능테스터19', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf20@test.com', 'pw', '성능테스터20', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf21@test.com', 'pw', '성능테스터21', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf22@test.com', 'pw', '성능테스터22', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf23@test.com', 'pw', '성능테스터23', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf24@test.com', 'pw', '성능테스터24', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf25@test.com', 'pw', '성능테스터25', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf26@test.com', 'pw', '성능테스터26', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf27@test.com', 'pw', '성능테스터27', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf28@test.com', 'pw', '성능테스터28', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf29@test.com', 'pw', '성능테스터29', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf30@test.com', 'pw', '성능테스터30', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf31@test.com', 'pw', '성능테스터31', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf32@test.com', 'pw', '성능테스터32', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf33@test.com', 'pw', '성능테스터33', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf34@test.com', 'pw', '성능테스터34', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf35@test.com', 'pw', '성능테스터35', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf36@test.com', 'pw', '성능테스터36', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf37@test.com', 'pw', '성능테스터37', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf38@test.com', 'pw', '성능테스터38', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf39@test.com', 'pw', '성능테스터39', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf40@test.com', 'pw', '성능테스터40', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf41@test.com', 'pw', '성능테스터41', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf42@test.com', 'pw', '성능테스터42', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf43@test.com', 'pw', '성능테스터43', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf44@test.com', 'pw', '성능테스터44', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf45@test.com', 'pw', '성능테스터45', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf46@test.com', 'pw', '성능테스터46', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf47@test.com', 'pw', '성능테스터47', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf48@test.com', 'pw', '성능테스터48', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf49@test.com', 'pw', '성능테스터49', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf50@test.com', 'pw', '성능테스터50', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf51@test.com', 'pw', '성능테스터51', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf52@test.com', 'pw', '성능테스터52', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf53@test.com', 'pw', '성능테스터53', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf54@test.com', 'pw', '성능테스터54', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf55@test.com', 'pw', '성능테스터55', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf56@test.com', 'pw', '성능테스터56', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf57@test.com', 'pw', '성능테스터57', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf58@test.com', 'pw', '성능테스터58', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf59@test.com', 'pw', '성능테스터59', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf60@test.com', 'pw', '성능테스터60', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf61@test.com', 'pw', '성능테스터61', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf62@test.com', 'pw', '성능테스터62', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf63@test.com', 'pw', '성능테스터63', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf64@test.com', 'pw', '성능테스터64', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf65@test.com', 'pw', '성능테스터65', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf66@test.com', 'pw', '성능테스터66', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf67@test.com', 'pw', '성능테스터67', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf68@test.com', 'pw', '성능테스터68', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf69@test.com', 'pw', '성능테스터69', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf70@test.com', 'pw', '성능테스터70', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf71@test.com', 'pw', '성능테스터71', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf72@test.com', 'pw', '성능테스터72', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf73@test.com', 'pw', '성능테스터73', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf74@test.com', 'pw', '성능테스터74', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf75@test.com', 'pw', '성능테스터75', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf76@test.com', 'pw', '성능테스터76', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf77@test.com', 'pw', '성능테스터77', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf78@test.com', 'pw', '성능테스터78', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf79@test.com', 'pw', '성능테스터79', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf80@test.com', 'pw', '성능테스터80', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf81@test.com', 'pw', '성능테스터81', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf82@test.com', 'pw', '성능테스터82', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf83@test.com', 'pw', '성능테스터83', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf84@test.com', 'pw', '성능테스터84', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf85@test.com', 'pw', '성능테스터85', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf86@test.com', 'pw', '성능테스터86', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf87@test.com', 'pw', '성능테스터87', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf88@test.com', 'pw', '성능테스터88', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf89@test.com', 'pw', '성능테스터89', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf90@test.com', 'pw', '성능테스터90', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf91@test.com', 'pw', '성능테스터91', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf92@test.com', 'pw', '성능테스터92', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf93@test.com', 'pw', '성능테스터93', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf94@test.com', 'pw', '성능테스터94', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf95@test.com', 'pw', '성능테스터95', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf96@test.com', 'pw', '성능테스터96', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf97@test.com', 'pw', '성능테스터97', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf98@test.com', 'pw', '성능테스터98', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf99@test.com', 'pw', '성능테스터99', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf100@test.com', 'pw', '성능테스터100', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf101@test.com', 'pw', '성능테스터101', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf102@test.com', 'pw', '성능테스터102', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf103@test.com', 'pw', '성능테스터103', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf104@test.com', 'pw', '성능테스터104', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf105@test.com', 'pw', '성능테스터105', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf106@test.com', 'pw', '성능테스터106', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf107@test.com', 'pw', '성능테스터107', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf108@test.com', 'pw', '성능테스터108', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf109@test.com', 'pw', '성능테스터109', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf110@test.com', 'pw', '성능테스터110', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf111@test.com', 'pw', '성능테스터111', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf112@test.com', 'pw', '성능테스터112', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf113@test.com', 'pw', '성능테스터113', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf114@test.com', 'pw', '성능테스터114', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf115@test.com', 'pw', '성능테스터115', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf116@test.com', 'pw', '성능테스터116', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf117@test.com', 'pw', '성능테스터117', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf118@test.com', 'pw', '성능테스터118', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf119@test.com', 'pw', '성능테스터119', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf120@test.com', 'pw', '성능테스터120', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf121@test.com', 'pw', '성능테스터121', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf122@test.com', 'pw', '성능테스터122', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf123@test.com', 'pw', '성능테스터123', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf124@test.com', 'pw', '성능테스터124', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf125@test.com', 'pw', '성능테스터125', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf126@test.com', 'pw', '성능테스터126', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf127@test.com', 'pw', '성능테스터127', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf128@test.com', 'pw', '성능테스터128', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf129@test.com', 'pw', '성능테스터129', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf130@test.com', 'pw', '성능테스터130', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf131@test.com', 'pw', '성능테스터131', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf132@test.com', 'pw', '성능테스터132', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf133@test.com', 'pw', '성능테스터133', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf134@test.com', 'pw', '성능테스터134', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf135@test.com', 'pw', '성능테스터135', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf136@test.com', 'pw', '성능테스터136', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf137@test.com', 'pw', '성능테스터137', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf138@test.com', 'pw', '성능테스터138', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf139@test.com', 'pw', '성능테스터139', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf140@test.com', 'pw', '성능테스터140', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf141@test.com', 'pw', '성능테스터141', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf142@test.com', 'pw', '성능테스터142', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf143@test.com', 'pw', '성능테스터143', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf144@test.com', 'pw', '성능테스터144', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf145@test.com', 'pw', '성능테스터145', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf146@test.com', 'pw', '성능테스터146', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf147@test.com', 'pw', '성능테스터147', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf148@test.com', 'pw', '성능테스터148', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf149@test.com', 'pw', '성능테스터149', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf150@test.com', 'pw', '성능테스터150', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf151@test.com', 'pw', '성능테스터151', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf152@test.com', 'pw', '성능테스터152', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf153@test.com', 'pw', '성능테스터153', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf154@test.com', 'pw', '성능테스터154', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf155@test.com', 'pw', '성능테스터155', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf156@test.com', 'pw', '성능테스터156', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf157@test.com', 'pw', '성능테스터157', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf158@test.com', 'pw', '성능테스터158', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf159@test.com', 'pw', '성능테스터159', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf160@test.com', 'pw', '성능테스터160', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf161@test.com', 'pw', '성능테스터161', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf162@test.com', 'pw', '성능테스터162', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf163@test.com', 'pw', '성능테스터163', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf164@test.com', 'pw', '성능테스터164', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf165@test.com', 'pw', '성능테스터165', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf166@test.com', 'pw', '성능테스터166', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf167@test.com', 'pw', '성능테스터167', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf168@test.com', 'pw', '성능테스터168', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf169@test.com', 'pw', '성능테스터169', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf170@test.com', 'pw', '성능테스터170', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf171@test.com', 'pw', '성능테스터171', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf172@test.com', 'pw', '성능테스터172', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf173@test.com', 'pw', '성능테스터173', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf174@test.com', 'pw', '성능테스터174', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf175@test.com', 'pw', '성능테스터175', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf176@test.com', 'pw', '성능테스터176', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf177@test.com', 'pw', '성능테스터177', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf178@test.com', 'pw', '성능테스터178', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf179@test.com', 'pw', '성능테스터179', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf180@test.com', 'pw', '성능테스터180', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf181@test.com', 'pw', '성능테스터181', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf182@test.com', 'pw', '성능테스터182', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf183@test.com', 'pw', '성능테스터183', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf184@test.com', 'pw', '성능테스터184', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf185@test.com', 'pw', '성능테스터185', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf186@test.com', 'pw', '성능테스터186', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf187@test.com', 'pw', '성능테스터187', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf188@test.com', 'pw', '성능테스터188', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf189@test.com', 'pw', '성능테스터189', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf190@test.com', 'pw', '성능테스터190', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf191@test.com', 'pw', '성능테스터191', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf192@test.com', 'pw', '성능테스터192', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf193@test.com', 'pw', '성능테스터193', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf194@test.com', 'pw', '성능테스터194', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf195@test.com', 'pw', '성능테스터195', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf196@test.com', 'pw', '성능테스터196', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf197@test.com', 'pw', '성능테스터197', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf198@test.com', 'pw', '성능테스터198', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf199@test.com', 'pw', '성능테스터199', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf200@test.com', 'pw', '성능테스터200', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf201@test.com', 'pw', '성능테스터201', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf202@test.com', 'pw', '성능테스터202', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (email, password_hash, name, phone, balance, version, created_at, updated_at) VALUES
('perf203@test.com', 'pw', '성능테스터203', '010-0000-0000', 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

