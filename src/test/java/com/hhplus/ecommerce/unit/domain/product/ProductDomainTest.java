package com.hhplus.ecommerce.unit.domain.product;


import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductNotFoundException;
import com.hhplus.ecommerce.domain.product.ProductOption;import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Product 도메인 엔티티 순수 단위 테스트
 *
 * 테스트 범위:
 * - 상품 생성 (팩토리 메서드)
 * - 옵션 관리 (추가, 찾기)
 * - 재고 관리 (차감, 복구, 재계산)
 * - 상태 전환 (자동 품절 상태 변경)
 * - 재고 조회 (hasStock, hasStockForOption, isSoldOut, isAvailable)
 * - 가격/정보 수정
 *
 * 특징:
 * - Mock 없음: 실제 Product, ProductOption 객체만 사용
 * - 순수 단위 테스트: 외부 의존성 제거
 * - 비즈니스 규칙 검증: 모든 도메인 규칙 테스트
 * - 실제 시나리오: 다중 옵션 재고 관리 등
 */
@DisplayName("Product 도메인 엔티티 테스트")
public class ProductDomainTest {

    // 테스트 상수
    private static final String TEST_PRODUCT_NAME = "테스트 상품";
    private static final String TEST_DESCRIPTION = "테스트 상품 설명";
    private static final Long TEST_PRICE = 50000L;

    // ===== PRODUCT CREATION TESTS =====
    @Nested
    @DisplayName("상품 생성 (createProduct)")
    class ProductCreationTests {

        @Test
        @DisplayName("정상 생성 - 모든 필드 초기화")
        void testCreateProduct_Success() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);

            assertNotNull(product);
            assertEquals(TEST_PRODUCT_NAME, product.getProductName());
            assertEquals(TEST_DESCRIPTION, product.getDescription());
            assertEquals(TEST_PRICE, product.getPrice());
            assertEquals(0, product.getTotalStock());
            assertEquals("IN_STOCK", product.getStatus());
            assertNotNull(product.getCreatedAt());
            assertNotNull(product.getUpdatedAt());
            assertTrue(product.getOptions().isEmpty());
        }

        @Test
        @DisplayName("생성 실패 - null 상품명")
        void testCreateProduct_NullProductName() {
            assertThrows(IllegalArgumentException.class, () ->
                Product.createProduct(null, TEST_DESCRIPTION, TEST_PRICE)
            );
        }

        @Test
        @DisplayName("생성 실패 - 빈 상품명")
        void testCreateProduct_BlankProductName() {
            assertThrows(IllegalArgumentException.class, () ->
                Product.createProduct("  ", TEST_DESCRIPTION, TEST_PRICE)
            );
        }

        @Test
        @DisplayName("생성 실패 - null 가격")
        void testCreateProduct_NullPrice() {
            assertThrows(IllegalArgumentException.class, () ->
                Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, null)
            );
        }

        @Test
        @DisplayName("생성 실패 - 0원 가격")
        void testCreateProduct_ZeroPrice() {
            assertThrows(IllegalArgumentException.class, () ->
                Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, 0L)
            );
        }

        @Test
        @DisplayName("생성 실패 - 음수 가격")
        void testCreateProduct_NegativePrice() {
            assertThrows(IllegalArgumentException.class, () ->
                Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, -50000L)
            );
        }
    }

    // ===== OPTION MANAGEMENT TESTS =====
    @Nested
    @DisplayName("옵션 관리")
    class OptionManagementTests {

        @Test
        @DisplayName("옵션 추가 - 정상")
        void testAddOption_Success() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(10).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

            product.addOption(option);

            assertEquals(1, product.getOptions().size());
            assertEquals(10, product.getTotalStock());
            assertEquals("IN_STOCK", product.getStatus());
        }

        @Test
        @DisplayName("옵션 추가 실패 - null 옵션")
        void testAddOption_NullOption() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);

            assertThrows(IllegalArgumentException.class, () ->
                product.addOption(null)
            );
        }

        @Test
        @DisplayName("다중 옵션 추가 - 순서 유지")
        void testAddMultipleOptions() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option1 = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(10).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            ProductOption option2 = ProductOption.builder().optionId(2L).productId(product.getProductId()).name("파랑").stock(20).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            ProductOption option3 = ProductOption.createOption(product.getProductId(), "초록", 15);

            product.addOption(option1);
            product.addOption(option2);
            product.addOption(option3);

            assertEquals(3, product.getOptions().size());
            assertEquals(45, product.getTotalStock());
            assertEquals(option1, product.getOptions().get(0));
            assertEquals(option2, product.getOptions().get(1));
            assertEquals(option3, product.getOptions().get(2));
        }

        @Test
        @DisplayName("옵션 찾기 - 성공")
        void testFindOptionById_Success() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder()
                    .optionId(100L)
                    .productId(product.getProductId())
                    .name("빨강")
                    .stock(10)
                    .version(1L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            product.addOption(option);

            var found = product.findOptionById(100L);

            assertTrue(found.isPresent());
            assertEquals(option, found.get());
        }

        @Test
        @DisplayName("옵션 찾기 - 없음")
        void testFindOptionById_NotFound() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);

            var found = product.findOptionById(999L);

            assertTrue(found.isEmpty());
        }
    }

    // ===== STOCK MANAGEMENT TESTS =====
    @Nested
    @DisplayName("재고 관리")
    class StockManagementTests {

        @Test
        @DisplayName("재고 차감 - 정상")
        void testDeductStock_Success() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder()
                    .optionId(1L)
                    .productId(product.getProductId())
                    .name("빨강")
                    .stock(100)
                    .version(1L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            product.addOption(option);

            product.deductStock(1L, 30);

            assertEquals(70, product.getTotalStock());
            assertEquals(70, option.getStock());
        }

        @Test
        @DisplayName("재고 차감 - 정확한 수량")
        void testDeductStock_ExactQuantity() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(50).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            product.deductStock(1L, 50);

            assertEquals(0, product.getTotalStock());
            assertEquals("SOLD_OUT", product.getStatus());
        }

        @Test
        @DisplayName("재고 차감 실패 - 수량 0")
        void testDeductStock_ZeroQuantity() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(50).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            assertThrows(IllegalArgumentException.class, () ->
                product.deductStock(1L, 0)
            );
        }

        @Test
        @DisplayName("재고 차감 실패 - 음수 수량")
        void testDeductStock_NegativeQuantity() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(50).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            assertThrows(IllegalArgumentException.class, () ->
                product.deductStock(1L, -10)
            );
        }

        @Test
        @DisplayName("재고 차감 실패 - 재고 부족")
        void testDeductStock_InsufficientStock() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(50).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            assertThrows(IllegalArgumentException.class, () ->
                product.deductStock(1L, 60)
            );
        }

        @Test
        @DisplayName("재고 차감 실패 - 존재하지 않는 옵션")
        void testDeductStock_OptionNotFound() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);

            assertThrows(ProductNotFoundException.class, () ->
                product.deductStock(999L, 10)
            );
        }

        @Test
        @DisplayName("재고 복구 - 정상")
        void testRestoreStock_Success() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(100).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            product.deductStock(1L, 70);
            assertEquals(30, product.getTotalStock());

            product.restoreStock(1L, 70);

            assertEquals(100, product.getTotalStock());
            assertEquals("IN_STOCK", product.getStatus());
        }

        @Test
        @DisplayName("재고 복구 실패 - 수량 0")
        void testRestoreStock_ZeroQuantity() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(50).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            assertThrows(IllegalArgumentException.class, () ->
                product.restoreStock(1L, 0)
            );
        }

        @Test
        @DisplayName("재고 복구 실패 - 음수 수량")
        void testRestoreStock_NegativeQuantity() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(50).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            assertThrows(IllegalArgumentException.class, () ->
                product.restoreStock(1L, -10)
            );
        }

        @Test
        @DisplayName("재고 복구 실패 - 존재하지 않는 옵션")
        void testRestoreStock_OptionNotFound() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);

            assertThrows(ProductNotFoundException.class, () ->
                product.restoreStock(999L, 10)
            );
        }
    }

    // ===== STOCK RECALCULATION TESTS =====
    @Nested
    @DisplayName("재고 재계산 및 상태 전환")
    class StockRecalculationTests {

        @Test
        @DisplayName("옵션 추가 후 총 재고 자동 계산")
        void testRecalculateTotalStock_AfterAddOption() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option1 = ProductOption.createOption(product.getProductId(), "빨강", 30);
            ProductOption option2 = ProductOption.createOption(product.getProductId(), "파랑", 20);
            ProductOption option3 = ProductOption.createOption(product.getProductId(), "초록", 50);

            product.addOption(option1);
            assertEquals(30, product.getTotalStock());

            product.addOption(option2);
            assertEquals(50, product.getTotalStock());

            product.addOption(option3);
            assertEquals(100, product.getTotalStock());
        }

        @Test
        @DisplayName("재고 차감 후 자동 상태 변경 - IN_STOCK")
        void testRecalculateTotalStock_RemainsInStock() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(100).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            product.deductStock(1L, 50);

            assertEquals(50, product.getTotalStock());
            assertEquals("IN_STOCK", product.getStatus());
        }

        @Test
        @DisplayName("재고 차감 후 자동 상태 변경 - SOLD_OUT (0)")
        void testRecalculateTotalStock_AutoSoldOut() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(100).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            product.deductStock(1L, 100);

            assertEquals(0, product.getTotalStock());
            assertEquals("SOLD_OUT", product.getStatus());
        }

        @Test
        @DisplayName("다중 옵션 - 전체 재고 소진 시 SOLD_OUT")
        void testRecalculateTotalStock_AllOptionsEmpty() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option1 = ProductOption.builder()
                    .optionId(1L)
                    .productId(product.getProductId())
                    .name("빨강")
                    .stock(30)
                    .version(1L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            ProductOption option2 = ProductOption.builder()
                    .optionId(2L)
                    .productId(product.getProductId())
                    .name("파랑")
                    .stock(20)
                    .version(1L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            product.addOption(option1);
            product.addOption(option2);
            assertEquals(50, product.getTotalStock());
            assertEquals("IN_STOCK", product.getStatus());

            product.deductStock(1L, 30);
            assertEquals(20, product.getTotalStock());
            assertEquals("IN_STOCK", product.getStatus());

            product.deductStock(2L, 20);
            assertEquals(0, product.getTotalStock());
            assertEquals("SOLD_OUT", product.getStatus());
        }

        @Test
        @DisplayName("재고 복구 후 상태 복구 - SOLD_OUT → IN_STOCK")
        void testRecalculateTotalStock_SoldOutToInStock() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(100).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            product.deductStock(1L, 100);
            assertEquals("SOLD_OUT", product.getStatus());

            product.restoreStock(1L, 50);
            assertEquals(50, product.getTotalStock());
            assertEquals("IN_STOCK", product.getStatus());
        }
    }

    // ===== STOCK INQUIRY TESTS =====
    @Nested
    @DisplayName("재고 조회 메서드")
    class StockInquiryTests {

        @Test
        @DisplayName("hasStock() - 재고 있음")
        void testHasStock_True() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(100).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            assertTrue(product.hasStock());
        }

        @Test
        @DisplayName("hasStock() - 재고 없음")
        void testHasStock_False() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(0).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            assertFalse(product.hasStock());
        }

        @Test
        @DisplayName("hasStockForOption() - 충분한 재고")
        void testHasStockForOption_Sufficient() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(100).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            assertTrue(product.hasStockForOption(1L, 50));
            assertTrue(product.hasStockForOption(1L, 100));
        }

        @Test
        @DisplayName("hasStockForOption() - 부족한 재고")
        void testHasStockForOption_Insufficient() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(100).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            assertFalse(product.hasStockForOption(1L, 101));
        }

        @Test
        @DisplayName("hasStockForOption() - 존재하지 않는 옵션")
        void testHasStockForOption_OptionNotFound() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);

            assertFalse(product.hasStockForOption(999L, 10));
        }

        @Test
        @DisplayName("isSoldOut() - true")
        void testIsSoldOut_True() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            // 새로 생성된 상품은 IN_STOCK 상태이므로, SOLD_OUT으로 변경
            product.recalculateTotalStock();  // 옵션이 없으므로 재고가 0이 되고 SOLD_OUT이 됨

            assertTrue(product.isSoldOut());
        }

        @Test
        @DisplayName("isSoldOut() - false")
        void testIsSoldOut_False() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(100).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            assertFalse(product.isSoldOut());
        }

        @Test
        @DisplayName("isAvailable() - true (IN_STOCK & 재고 있음)")
        void testIsAvailable_True() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(100).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            assertTrue(product.isAvailable());
        }

        @Test
        @DisplayName("isAvailable() - false (SOLD_OUT)")
        void testIsAvailable_False_SoldOut() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);

            assertFalse(product.isAvailable());
        }

        @Test
        @DisplayName("isAvailable() - false (IN_STOCK but no stock)")
        void testIsAvailable_False_NoStock() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("빨강").stock(0).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            assertFalse(product.isAvailable());
        }
    }

    // ===== PRICE UPDATE TESTS =====
    @Nested
    @DisplayName("가격 수정")
    class PriceUpdateTests {

        @Test
        @DisplayName("가격 수정 - 정상")
        void testUpdatePrice_Success() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            LocalDateTime beforeUpdate = product.getUpdatedAt();

            product.updatePrice(60000L);

            assertEquals(60000L, product.getPrice());
            assertTrue(product.getUpdatedAt().isAfter(beforeUpdate) ||
                      product.getUpdatedAt().isEqual(beforeUpdate));
        }

        @Test
        @DisplayName("가격 수정 실패 - null")
        void testUpdatePrice_Null() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);

            assertThrows(IllegalArgumentException.class, () ->
                product.updatePrice(null)
            );
        }

        @Test
        @DisplayName("가격 수정 실패 - 0")
        void testUpdatePrice_Zero() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);

            assertThrows(IllegalArgumentException.class, () ->
                product.updatePrice(0L)
            );
        }

        @Test
        @DisplayName("가격 수정 실패 - 음수")
        void testUpdatePrice_Negative() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);

            assertThrows(IllegalArgumentException.class, () ->
                product.updatePrice(-50000L)
            );
        }
    }

    // ===== INFO UPDATE TESTS =====
    @Nested
    @DisplayName("상품 정보 수정")
    class InfoUpdateTests {

        @Test
        @DisplayName("설명 수정 - 정상")
        void testUpdateInfo_Success() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);
            String newDescription = "수정된 설명";
            LocalDateTime beforeUpdate = product.getUpdatedAt();

            product.updateInfo(newDescription);

            assertEquals(newDescription, product.getDescription());
            assertTrue(product.getUpdatedAt().isAfter(beforeUpdate) ||
                      product.getUpdatedAt().isEqual(beforeUpdate));
        }

        @Test
        @DisplayName("설명 수정 - null 입력")
        void testUpdateInfo_Null() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);

            product.updateInfo(null);

            assertNull(product.getDescription());
        }

        @Test
        @DisplayName("설명 수정 - 빈 문자열")
        void testUpdateInfo_Empty() {
            Product product = Product.createProduct(TEST_PRODUCT_NAME, TEST_DESCRIPTION, TEST_PRICE);

            product.updateInfo("");

            assertEquals("", product.getDescription());
        }
    }

    // ===== REAL-WORLD SCENARIO TESTS =====
    @Nested
    @DisplayName("실제 시나리오 테스트")
    class RealWorldScenarioTests {

        @Test
        @DisplayName("시나리오 1: 다중 옵션 상품의 주문 처리")
        void scenario1_MultiOptionOrderProcessing() {
            // 상품 생성: 셔츠 (빨강/파랑 2가지, 각 50개)
            Product product = Product.createProduct("셔츠", "편한 면 셔츠", 30000L);
            ProductOption redOption = ProductOption.builder()
                    .optionId(1L)
                    .productId(product.getProductId())
                    .name("빨강")
                    .stock(50)
                    .version(1L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            ProductOption blueOption = ProductOption.builder()
                    .optionId(2L)
                    .productId(product.getProductId())
                    .name("파랑")
                    .stock(50)
                    .version(1L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            product.addOption(redOption);
            product.addOption(blueOption);
            assertEquals(100, product.getTotalStock());
            assertEquals("IN_STOCK", product.getStatus());

            // 주문 1: 빨강 20개
            product.deductStock(1L, 20);
            assertEquals(80, product.getTotalStock());
            assertTrue(product.isAvailable());

            // 주문 2: 파랑 30개
            product.deductStock(2L, 30);
            assertEquals(50, product.getTotalStock());
            assertTrue(product.isAvailable());

            // 주문 취소: 빨강 20개 반품
            product.restoreStock(1L, 20);
            assertEquals(70, product.getTotalStock());
            assertEquals("IN_STOCK", product.getStatus());

            // 주문 3: 빨강 50개, 파랑 20개
            product.deductStock(1L, 50);
            product.deductStock(2L, 20);
            assertEquals(0, product.getTotalStock());
            assertEquals("SOLD_OUT", product.getStatus());

            // 전량 반품 후 상태 복구
            product.restoreStock(1L, 50);
            product.restoreStock(2L, 20);
            assertEquals(70, product.getTotalStock());
            assertEquals("IN_STOCK", product.getStatus());
        }

        @Test
        @DisplayName("시나리오 2: 단계적 품절 처리")
        void scenario2_GradualSoldOutProcess() {
            Product product = Product.createProduct("노트북", "고성능 노트북", 1500000L);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("검정").stock(10).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            assertTrue(product.isAvailable());

            // 점진적 판매
            for (int i = 0; i < 9; i++) {
                product.deductStock(1L, 1);
                assertEquals(9 - i, product.getTotalStock());
                assertTrue(product.isAvailable());
            }

            // 마지막 1개 판매
            product.deductStock(1L, 1);
            assertEquals(0, product.getTotalStock());
            assertTrue(product.isSoldOut());
            assertFalse(product.isAvailable());
        }

        @Test
        @DisplayName("시나리오 3: 가격 인상 및 재고 관리")
        void scenario3_PriceIncreaseAndInventory() {
            Product product = Product.createProduct("화장품", "프리미엄 크림", 50000L);
            ProductOption option = ProductOption.builder().optionId(1L).productId(product.getProductId()).name("대용량").stock(100).version(1L).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            product.addOption(option);

            assertEquals(50000L, product.getPrice());

            // 수요 증가로 가격 인상
            product.updatePrice(55000L);
            assertEquals(55000L, product.getPrice());

            // 판매
            product.deductStock(1L, 40);
            assertEquals(60, product.getTotalStock());

            // 추가 주문
            product.deductStock(1L, 60);
            assertEquals(0, product.getTotalStock());
            assertEquals("SOLD_OUT", product.getStatus());
        }

        @Test
        @DisplayName("시나리오 4: 옵션별 재고 편차")
        void scenario4_OptionStockVariance() {
            Product product = Product.createProduct("신발", "운동화", 80000L);

            // 다양한 크기별 재고
            ProductOption size37 = ProductOption.builder()
                    .optionId(1L)
                    .productId(product.getProductId())
                    .name("37사이즈")
                    .stock(5)
                    .version(1L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            ProductOption size40 = ProductOption.builder()
                    .optionId(2L)
                    .productId(product.getProductId())
                    .name("40사이즈")
                    .stock(50)
                    .version(1L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            ProductOption size43 = ProductOption.builder()
                    .optionId(3L)
                    .productId(product.getProductId())
                    .name("43사이즈")
                    .stock(30)
                    .version(1L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            product.addOption(size37);
            product.addOption(size40);
            product.addOption(size43);

            assertEquals(85, product.getTotalStock());

            // 인기 사이즈(40) 판매
            product.deductStock(2L, 50);
            assertEquals(35, product.getTotalStock());

            // 소수 사이즈(37) 판매
            product.deductStock(1L, 5);
            assertEquals(30, product.getTotalStock());

            // 남은 재고는 43사이즈만
            assertEquals(30, size43.getStock());
            assertTrue(product.hasStockForOption(3L, 30));
            assertFalse(product.hasStockForOption(1L, 1));
        }
    }
}
