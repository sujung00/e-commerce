package com.hhplus.ecommerce.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderItem 도메인 엔티티 순수 단위 테스트
 *
 * 테스트 범위:
 * - 주문 항목 생성 (팩토리 메서드 + 비즈니스 규칙)
 * - 가격 계산 (단가, 소계, 할인)
 * - 유효성 검증 (수량, 가격, 스냅샷)
 * - 할인 로직 (할인율 적용, 할인액 계산)
 * - 정보 조회 (상품명, 수량, 가격)
 *
 * 특징:
 * - Mock 없음: 실제 OrderItem 객체만 사용
 * - 순수 단위 테스트: 외부 의존성 제거
 * - 비즈니스 규칙 검증: 모든 도메인 규칙 테스트
 * - 재무 계산 정확성: 가격, 할인, 소계 검증
 * - 스냅샷 검증: 주문 시점의 상품정보 보존
 */
@DisplayName("OrderItem 도메인 엔티티 테스트")
public class OrderItemDomainTest {

    // 테스트 상수
    private static final Long TEST_PRODUCT_ID = 100L;
    private static final Long TEST_OPTION_ID = 1L;
    private static final String TEST_PRODUCT_NAME = "테스트 상품";
    private static final String TEST_OPTION_NAME = "옵션1";
    private static final Integer TEST_QUANTITY = 3;
    private static final Long TEST_UNIT_PRICE = 50000L;

    // ===== ORDERITEM CREATION TESTS =====
    @Nested
    @DisplayName("주문 항목 생성 (createOrderItem)")
    class OrderItemCreationTests {

        @Test
        @DisplayName("정상 생성 - 모든 필드 초기화")
        void testCreateOrderItem_Success() {
            OrderItem item = OrderItem.createOrderItem(
                TEST_PRODUCT_ID, TEST_OPTION_ID, TEST_PRODUCT_NAME,
                TEST_OPTION_NAME, TEST_QUANTITY, TEST_UNIT_PRICE
            );

            assertNotNull(item);
            assertEquals(TEST_PRODUCT_ID, item.getProductId());
            assertEquals(TEST_OPTION_ID, item.getOptionId());
            assertEquals(TEST_PRODUCT_NAME, item.getProductName());
            assertEquals(TEST_OPTION_NAME, item.getOptionName());
            assertEquals(TEST_QUANTITY, item.getQuantity());
            assertEquals(TEST_UNIT_PRICE, item.getUnitPrice());
            assertEquals(TEST_UNIT_PRICE * TEST_QUANTITY, item.getSubtotal());
            assertNotNull(item.getCreatedAt());
        }

        @Test
        @DisplayName("생성 실패 - null 수량")
        void testCreateOrderItem_NullQuantity() {
            assertThrows(IllegalArgumentException.class, () ->
                OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID, TEST_PRODUCT_NAME,
                    TEST_OPTION_NAME, null, TEST_UNIT_PRICE)
            );
        }

        @Test
        @DisplayName("생성 실패 - 0 수량")
        void testCreateOrderItem_ZeroQuantity() {
            assertThrows(IllegalArgumentException.class, () ->
                OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID, TEST_PRODUCT_NAME,
                    TEST_OPTION_NAME, 0, TEST_UNIT_PRICE)
            );
        }

        @Test
        @DisplayName("생성 실패 - 음수 수량")
        void testCreateOrderItem_NegativeQuantity() {
            assertThrows(IllegalArgumentException.class, () ->
                OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID, TEST_PRODUCT_NAME,
                    TEST_OPTION_NAME, -5, TEST_UNIT_PRICE)
            );
        }

        @Test
        @DisplayName("생성 실패 - null 단가")
        void testCreateOrderItem_NullUnitPrice() {
            assertThrows(IllegalArgumentException.class, () ->
                OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID, TEST_PRODUCT_NAME,
                    TEST_OPTION_NAME, TEST_QUANTITY, null)
            );
        }

        @Test
        @DisplayName("생성 실패 - 0 단가")
        void testCreateOrderItem_ZeroUnitPrice() {
            assertThrows(IllegalArgumentException.class, () ->
                OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID, TEST_PRODUCT_NAME,
                    TEST_OPTION_NAME, TEST_QUANTITY, 0L)
            );
        }

        @Test
        @DisplayName("생성 실패 - 음수 단가")
        void testCreateOrderItem_NegativeUnitPrice() {
            assertThrows(IllegalArgumentException.class, () ->
                OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID, TEST_PRODUCT_NAME,
                    TEST_OPTION_NAME, TEST_QUANTITY, -50000L)
            );
        }

        @Test
        @DisplayName("생성 실패 - null 상품명")
        void testCreateOrderItem_NullProductName() {
            assertThrows(IllegalArgumentException.class, () ->
                OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID, null,
                    TEST_OPTION_NAME, TEST_QUANTITY, TEST_UNIT_PRICE)
            );
        }

        @Test
        @DisplayName("생성 실패 - 빈 상품명")
        void testCreateOrderItem_BlankProductName() {
            assertThrows(IllegalArgumentException.class, () ->
                OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID, "  ",
                    TEST_OPTION_NAME, TEST_QUANTITY, TEST_UNIT_PRICE)
            );
        }

        @Test
        @DisplayName("생성 실패 - null 옵션명")
        void testCreateOrderItem_NullOptionName() {
            assertThrows(IllegalArgumentException.class, () ->
                OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID, TEST_PRODUCT_NAME,
                    null, TEST_QUANTITY, TEST_UNIT_PRICE)
            );
        }

        @Test
        @DisplayName("생성 실패 - 빈 옵션명")
        void testCreateOrderItem_BlankOptionName() {
            assertThrows(IllegalArgumentException.class, () ->
                OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID, TEST_PRODUCT_NAME,
                    "  ", TEST_QUANTITY, TEST_UNIT_PRICE)
            );
        }
    }

    // ===== PRICE CALCULATION TESTS =====
    @Nested
    @DisplayName("가격 계산")
    class PriceCalculationTests {

        @Test
        @DisplayName("소계 계산 - 정상")
        void testSubtotalCalculation_Normal() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 3, 50000L);

            assertEquals(150000L, item.getSubtotal());
            assertEquals(150000L, item.getLineTotal());
        }

        @Test
        @DisplayName("소계 계산 - 단일 수량")
        void testSubtotalCalculation_SingleQuantity() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 1, 50000L);

            assertEquals(50000L, item.getSubtotal());
            assertEquals(50000L, item.getLineTotal());
        }

        @Test
        @DisplayName("소계 계산 - 대량 주문")
        void testSubtotalCalculation_LargeQuantity() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 100, 10000L);

            assertEquals(1000000L, item.getSubtotal());
        }

        @Test
        @DisplayName("소계 계산 - 높은 단가")
        void testSubtotalCalculation_HighPrice() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 5, 1000000L);

            assertEquals(5000000L, item.getSubtotal());
        }

        @Test
        @DisplayName("단가 조회")
        void testGetPrice() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 10, 25000L);

            assertEquals(25000L, item.getPrice());
        }

        @Test
        @DisplayName("수량 조회")
        void testGetOrderQuantity() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 42, 10000L);

            assertEquals(42, item.getOrderQuantity());
        }

        @DisplayName("소계는 수량과 단가의 곱")
        @ParameterizedTest(name = "수량={0}, 단가={1}, 소계={2}")
        @CsvSource({
            "1, 10000, 10000",
            "2, 20000, 40000",
            "5, 15000, 75000",
            "10, 5000, 50000",
            "100, 1000, 100000"
        })
        void testSubtotalConsistency(int quantity, long unitPrice, long expectedSubtotal) {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, quantity, unitPrice);

            assertEquals(expectedSubtotal, item.getSubtotal());
            assertEquals(expectedSubtotal, item.getLineTotal());
        }
    }

    // ===== DISCOUNT CALCULATION TESTS =====
    @Nested
    @DisplayName("할인 계산")
    class DiscountCalculationTests {

        @Test
        @DisplayName("할인율 적용 - 10% 할인")
        void testCalculateDiscountedAmount_10Percent() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 10, 10000L);

            long discountedAmount = item.calculateDiscountedAmount(0.1);

            assertEquals(90000L, discountedAmount); // 100000 * 0.9
        }

        @Test
        @DisplayName("할인율 적용 - 50% 할인")
        void testCalculateDiscountedAmount_50Percent() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 10, 20000L);

            long discountedAmount = item.calculateDiscountedAmount(0.5);

            assertEquals(100000L, discountedAmount); // 200000 * 0.5
        }

        @Test
        @DisplayName("할인율 적용 - 할인 없음 (0%)")
        void testCalculateDiscountedAmount_NoDiscount() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 5, 20000L);

            long discountedAmount = item.calculateDiscountedAmount(0.0);

            assertEquals(100000L, discountedAmount); // 100000 * 1.0
        }

        @Test
        @DisplayName("할인액 계산 - 10% 할인")
        void testCalculateDiscountAmount_10Percent() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 10, 10000L);

            long discountAmount = item.calculateDiscountAmount(0.1);

            assertEquals(10000L, discountAmount); // 100000 * 0.1
        }

        @Test
        @DisplayName("할인액 계산 - 전액 할인 (100%)")
        void testCalculateDiscountAmount_FullDiscount() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 5, 50000L);

            long discountAmount = item.calculateDiscountAmount(1.0);

            assertEquals(250000L, discountAmount); // 250000 * 1.0
        }

        @Test
        @DisplayName("할인액 계산 - 할인 없음 (0%)")
        void testCalculateDiscountAmount_NoDiscount() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 5, 50000L);

            long discountAmount = item.calculateDiscountAmount(0.0);

            assertEquals(0L, discountAmount);
        }

        @Test
        @DisplayName("할인율 검증 실패 - 음수")
        void testCalculateDiscountAmount_NegativeRate() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 5, 50000L);

            assertThrows(IllegalArgumentException.class, () ->
                item.calculateDiscountAmount(-0.1)
            );
        }

        @Test
        @DisplayName("할인율 검증 실패 - 1.0 초과")
        void testCalculateDiscountAmount_ExceedsMax() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 5, 50000L);

            assertThrows(IllegalArgumentException.class, () ->
                item.calculateDiscountAmount(1.1)
            );
        }

        @Test
        @DisplayName("할인율 적용 검증 실패 - 음수")
        void testCalculateDiscountedAmount_NegativeRate() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 5, 50000L);

            assertThrows(IllegalArgumentException.class, () ->
                item.calculateDiscountedAmount(-0.1)
            );
        }

        @Test
        @DisplayName("할인율 적용 검증 실패 - 1.0 초과")
        void testCalculateDiscountedAmount_ExceedsMax() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 5, 50000L);

            assertThrows(IllegalArgumentException.class, () ->
                item.calculateDiscountedAmount(1.5)
            );
        }

        @Test
        @DisplayName("할인액 + 할인 후 금액 = 소계")
        void testDiscountConsistency() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 10, 10000L);

            double discountRate = 0.25; // 25%
            long discountAmount = item.calculateDiscountAmount(discountRate);
            long discountedAmount = item.calculateDiscountedAmount(discountRate);

            assertEquals(item.getSubtotal(), discountAmount + discountedAmount);
        }

        @DisplayName("다양한 할인율 테스트")
        @ParameterizedTest(name = "할인율={0}")
        @ValueSource(doubles = {0.0, 0.1, 0.25, 0.5, 0.75, 1.0})
        void testVariousDiscountRates(double rate) {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 100, 1000L);

            long discountAmount = item.calculateDiscountAmount(rate);
            long discountedAmount = item.calculateDiscountedAmount(rate);

            assertEquals(100000L, discountAmount + discountedAmount);
            assertTrue(discountAmount >= 0);
            assertTrue(discountedAmount >= 0);
        }
    }

    // ===== PRODUCT INFO SNAPSHOT TESTS =====
    @Nested
    @DisplayName("상품 정보 스냅샷")
    class ProductInfoSnapshotTests {

        @Test
        @DisplayName("상품명과 옵션명 스냅샷 저장")
        void testProductInfoSnapshot() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                "원본 상품명", "원본 옵션명", 5, 50000L);

            assertEquals("원본 상품명", item.getProductName());
            assertEquals("원본 옵션명", item.getOptionName());
        }

        @Test
        @DisplayName("상품명과 옵션명 조합 조회")
        void testGetProductDisplayName() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                "셔츠", "빨강", 3, 30000L);

            assertEquals("셔츠 - 빨강", item.getProductDisplayName());
        }

        @Test
        @DisplayName("다양한 상품명 조합")
        void testProductDisplayNameVariations() {
            OrderItem item1 = OrderItem.createOrderItem(100L, 1L, "노트북", "검정", 1, 1000000L);
            OrderItem item2 = OrderItem.createOrderItem(200L, 2L, "마우스", "무선", 2, 50000L);
            OrderItem item3 = OrderItem.createOrderItem(300L, 3L, "키보드", "기계식 RGB", 1, 150000L);

            assertEquals("노트북 - 검정", item1.getProductDisplayName());
            assertEquals("마우스 - 무선", item2.getProductDisplayName());
            assertEquals("키보드 - 기계식 RGB", item3.getProductDisplayName());
        }
    }

    // ===== VALIDITY CHECK TESTS =====
    @Nested
    @DisplayName("유효성 검증 (isValid)")
    class ValidityCheckTests {

        @Test
        @DisplayName("유효한 주문 항목")
        void testIsValid_True() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 5, 50000L);

            assertTrue(item.isValid());
        }

        @Test
        @DisplayName("유효한 주문 항목 - 다양한 값")
        void testIsValid_VariousValidItems() {
            OrderItem item1 = OrderItem.createOrderItem(100L, 1L, "상품1", "옵션1", 1, 1000L);
            OrderItem item2 = OrderItem.createOrderItem(200L, 2L, "상품2", "옵션2", 100, 1000000L);
            OrderItem item3 = OrderItem.createOrderItem(300L, 3L, "상품3", "옵션3", 1000, 100L);

            assertTrue(item1.isValid());
            assertTrue(item2.isValid());
            assertTrue(item3.isValid());
        }

        @Test
        @DisplayName("소계는 항상 일관성 있음 (quantity * unitPrice)")
        void testSubtotalConsistency_AlwaysValid() {
            OrderItem item = OrderItem.createOrderItem(TEST_PRODUCT_ID, TEST_OPTION_ID,
                TEST_PRODUCT_NAME, TEST_OPTION_NAME, 10, 25000L);

            assertTrue(item.isValid());
            assertEquals(250000L, item.getSubtotal());
            assertEquals(item.getQuantity() * item.getPrice(), item.getSubtotal());
        }
    }

    // ===== REAL-WORLD SCENARIO TESTS =====
    @Nested
    @DisplayName("실제 시나리오 테스트")
    class RealWorldScenarioTests {

        @Test
        @DisplayName("시나리오 1: 복합 상품 주문 항목")
        void scenario1_ComplexProductItem() {
            // 프리미엄 노트북 3개 주문
            OrderItem laptop = OrderItem.createOrderItem(1001L, 5L,
                "프리미엄 노트북", "SSD 1TB", 3, 1500000L);

            assertEquals("프리미엄 노트북 - SSD 1TB", laptop.getProductDisplayName());
            assertEquals(4500000L, laptop.getSubtotal());

            // 20% 쿠폰 할인 적용
            long discountAmount = laptop.calculateDiscountAmount(0.2);
            long finalAmount = laptop.calculateDiscountedAmount(0.2);

            assertEquals(900000L, discountAmount);
            assertEquals(3600000L, finalAmount);
        }

        @Test
        @DisplayName("시나리오 2: 다중 항목 주문 구성")
        void scenario2_MultipleItems() {
            // 항목 1: 셔츠 10개
            OrderItem shirt = OrderItem.createOrderItem(2001L, 10L,
                "면 셔츠", "흰색 M", 10, 30000L);

            // 항목 2: 청바지 5개
            OrderItem jeans = OrderItem.createOrderItem(2002L, 11L,
                "데님 청바지", "파란색 32인치", 5, 80000L);

            // 항목 3: 신발 2개
            OrderItem shoes = OrderItem.createOrderItem(2003L, 12L,
                "운동화", "검은색 270", 2, 150000L);

            // 주문 총액
            long totalAmount = shirt.getSubtotal() + jeans.getSubtotal() + shoes.getSubtotal();
            assertEquals(300000L + 400000L + 300000L, totalAmount);

            // 10% 할인 적용
            long totalDiscount = shirt.calculateDiscountAmount(0.1) +
                                jeans.calculateDiscountAmount(0.1) +
                                shoes.calculateDiscountAmount(0.1);
            assertEquals(100000L, totalDiscount); // 1000000 * 0.1

            long finalTotal = totalAmount - totalDiscount;
            assertEquals(900000L, finalTotal);
        }

        @Test
        @DisplayName("시나리오 3: 대량 주문")
        void scenario3_BulkOrder() {
            // 상품 1000개 대량 주문
            OrderItem bulkItem = OrderItem.createOrderItem(3001L, 20L,
                "볼펜", "검정색", 1000, 1000L);

            assertEquals(1000000L, bulkItem.getSubtotal());
            assertEquals(1000, bulkItem.getOrderQuantity());

            // 벌크 할인: 15%
            long discountAmount = bulkItem.calculateDiscountAmount(0.15);
            long discountedAmount = bulkItem.calculateDiscountedAmount(0.15);

            assertEquals(150000L, discountAmount);
            assertEquals(850000L, discountedAmount);
        }

        @Test
        @DisplayName("시나리오 4: 고가 상품 주문")
        void scenario4_ExpensiveProduct() {
            // 고급 시계 5개
            OrderItem watch = OrderItem.createOrderItem(4001L, 30L,
                "스위스 명품 시계", "금장", 5, 5000000L);

            assertEquals(25000000L, watch.getSubtotal());
            assertTrue(watch.isValid());

            // VIP 할인: 5%
            long vipDiscount = watch.calculateDiscountAmount(0.05);
            long vipPrice = watch.calculateDiscountedAmount(0.05);

            assertEquals(1250000L, vipDiscount);
            assertEquals(23750000L, vipPrice);
        }

        @Test
        @DisplayName("시나리오 5: 단가 매우 낮은 상품")
        void scenario5_LowPriceProduct() {
            // 껌 500개 주문 (개당 500원)
            OrderItem gum = OrderItem.createOrderItem(5001L, 40L,
                "씹는 껌", "민트향", 500, 500L);

            assertEquals(250000L, gum.getSubtotal());
            assertTrue(gum.isValid());

            // 대량 구매 할인: 25%
            long discount = gum.calculateDiscountAmount(0.25);
            long finalPrice = gum.calculateDiscountedAmount(0.25);

            assertEquals(62500L, discount);
            assertEquals(187500L, finalPrice);
        }

        @Test
        @DisplayName("시나리오 6: 순차적 할인 적용 (누적 할인 아님)")
        void scenario6_SequentialDiscounts() {
            OrderItem item = OrderItem.createOrderItem(6001L, 50L,
                "의류", "사이즈 M", 10, 50000L);

            long subtotal = item.getSubtotal(); // 500000

            // 첫 번째 쿠폰: 10%
            long discount1 = item.calculateDiscountAmount(0.1); // 50000

            // 두 번째 쿠폰: 5% (원래 금액 기준)
            long discount2 = item.calculateDiscountAmount(0.05); // 25000
            long totalDiscount = discount1 + discount2; // 75000

            assertEquals(500000L, subtotal);
            assertEquals(425000L, subtotal - totalDiscount);
        }

        @Test
        @DisplayName("시나리오 7: 주문 항목의 모든 요소 검증")
        void scenario7_CompleteItemValidation() {
            OrderItem item = OrderItem.createOrderItem(7001L, 60L,
                "고급 커피 머신", "자동 에스프레소", 2, 300000L);

            // 모든 필드 검증
            assertEquals(7001L, item.getProductId());
            assertEquals(60L, item.getOptionId());
            assertEquals("고급 커피 머신", item.getProductName());
            assertEquals("자동 에스프레소", item.getOptionName());
            assertEquals(2, item.getQuantity());
            assertEquals(2, item.getOrderQuantity());
            assertEquals(300000L, item.getPrice());
            assertEquals(300000L, item.getUnitPrice());
            assertEquals(600000L, item.getSubtotal());
            assertEquals(600000L, item.getLineTotal());
            assertEquals("고급 커피 머신 - 자동 에스프레소", item.getProductDisplayName());
            assertTrue(item.isValid());
            assertNotNull(item.getCreatedAt());

            // 할인 검증
            long discount30 = item.calculateDiscountAmount(0.3);
            long discounted30 = item.calculateDiscountedAmount(0.3);
            assertEquals(180000L, discount30);
            assertEquals(420000L, discounted30);
        }
    }
}
