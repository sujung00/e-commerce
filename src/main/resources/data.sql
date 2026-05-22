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
