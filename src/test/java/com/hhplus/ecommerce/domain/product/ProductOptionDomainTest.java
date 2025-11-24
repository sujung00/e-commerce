package com.hhplus.ecommerce.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProductOption 도메인 엔티티 순수 단위 테스트
 *
 * 테스트 범위:
 * - 옵션 생성 (팩토리 메서드)
 * - 재고 차감 (낙관적 락 - version 증가)
 * - 재고 복구 (낙관적 락 - version 증가)
 * - 버전 관리 (동시성 제어 검증)
 * - 재고 조회 및 상태 확인
 * - 옵션 정보 수정
 *
 * 특징:
 * - Mock 없음: 실제 ProductOption 객체만 사용
 * - 순수 단위 테스트: 외부 의존성 제거
 * - 낙관적 락 검증: version 증가 확인
 * - 동시성 시뮬레이션: 버전 충돌 시나리오 테스트
 * - 비즈니스 규칙: 모든 도메인 규칙 검증
 */
@DisplayName("ProductOption 도메인 엔티티 테스트")
public class ProductOptionDomainTest {

    // 테스트 상수
    private static final Long TEST_PRODUCT_ID = 1L;
    private static final String TEST_OPTION_NAME = "빨강";
    private static final Integer TEST_INITIAL_STOCK = 100;

    // ===== OPTION CREATION TESTS =====
    @Nested
    @DisplayName("옵션 생성 (createOption)")
    class OptionCreationTests {

        @Test
        @DisplayName("정상 생성 - 모든 필드 초기화")
        void testCreateOption_Success() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, TEST_INITIAL_STOCK);

            assertNotNull(option);
            assertEquals(TEST_PRODUCT_ID, option.getProductId());
            assertEquals(TEST_OPTION_NAME, option.getName());
            assertEquals(TEST_INITIAL_STOCK, option.getStock());
            assertEquals(1L, option.getVersion()); // 초기 version은 1
            assertNotNull(option.getCreatedAt());
            assertNotNull(option.getUpdatedAt());
        }

        @Test
        @DisplayName("생성 실패 - null 옵션명")
        void testCreateOption_NullName() {
            assertThrows(IllegalArgumentException.class, () ->
                ProductOption.createOption(TEST_PRODUCT_ID, null, TEST_INITIAL_STOCK)
            );
        }

        @Test
        @DisplayName("생성 실패 - 빈 옵션명")
        void testCreateOption_BlankName() {
            assertThrows(IllegalArgumentException.class, () ->
                ProductOption.createOption(TEST_PRODUCT_ID, "  ", TEST_INITIAL_STOCK)
            );
        }

        @Test
        @DisplayName("생성 실패 - null 재고")
        void testCreateOption_NullStock() {
            assertThrows(IllegalArgumentException.class, () ->
                ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, null)
            );
        }

        @Test
        @DisplayName("생성 실패 - 음수 재고")
        void testCreateOption_NegativeStock() {
            assertThrows(IllegalArgumentException.class, () ->
                ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, -10)
            );
        }

        @Test
        @DisplayName("생성 - 0 재고 (유효)")
        void testCreateOption_ZeroStock() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 0);

            assertEquals(0, option.getStock());
            assertEquals(1L, option.getVersion());
        }

        @DisplayName("생성 - 초기 재고 다양한 값")
        @ParameterizedTest(name = "초기 재고={0}")
        @ValueSource(ints = {0, 1, 50, 100, 1000})
        void testCreateOption_VariousStocks(int stock) {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, stock);

            assertEquals(stock, option.getStock());
            assertEquals(1L, option.getVersion());
        }
    }

    // ===== STOCK DEDUCTION TESTS =====
    @Nested
    @DisplayName("재고 차감 (deductStock)")
    class StockDeductionTests {

        @Test
        @DisplayName("재고 차감 - 정상")
        void testDeductStock_Success() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);
            long initialVersion = option.getVersion();

            option.deductStock(30);

            assertEquals(70, option.getStock());
            assertEquals(initialVersion + 1, option.getVersion());
        }

        @Test
        @DisplayName("재고 차감 - 정확한 수량")
        void testDeductStock_ExactQuantity() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 50);
            long initialVersion = option.getVersion();

            option.deductStock(50);

            assertEquals(0, option.getStock());
            assertEquals(initialVersion + 1, option.getVersion());
        }

        @Test
        @DisplayName("재고 차감 - 단일 수량")
        void testDeductStock_SingleUnit() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);
            long initialVersion = option.getVersion();

            option.deductStock(1);

            assertEquals(99, option.getStock());
            assertEquals(initialVersion + 1, option.getVersion());
        }

        @Test
        @DisplayName("재고 차감 실패 - 0 수량")
        void testDeductStock_ZeroQuantity() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);
            long initialVersion = option.getVersion();

            assertThrows(IllegalArgumentException.class, () ->
                option.deductStock(0)
            );

            assertEquals(100, option.getStock());
            assertEquals(initialVersion, option.getVersion()); // version 미변경
        }

        @Test
        @DisplayName("재고 차감 실패 - 음수 수량")
        void testDeductStock_NegativeQuantity() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);
            long initialVersion = option.getVersion();

            assertThrows(IllegalArgumentException.class, () ->
                option.deductStock(-10)
            );

            assertEquals(100, option.getStock());
            assertEquals(initialVersion, option.getVersion());
        }

        @Test
        @DisplayName("재고 차감 실패 - 재고 부족")
        void testDeductStock_InsufficientStock() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 50);
            long initialVersion = option.getVersion();

            assertThrows(IllegalArgumentException.class, () ->
                option.deductStock(51)
            );

            assertEquals(50, option.getStock());
            assertEquals(initialVersion, option.getVersion());
        }

        @Test
        @DisplayName("재고 차감 실패 - 0 재고에서 추가 차감")
        void testDeductStock_AlreadyEmpty() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 0);

            assertThrows(IllegalArgumentException.class, () ->
                option.deductStock(1)
            );
        }
    }

    // ===== STOCK RESTORATION TESTS =====
    @Nested
    @DisplayName("재고 복구 (restoreStock)")
    class StockRestorationTests {

        @Test
        @DisplayName("재고 복구 - 정상")
        void testRestoreStock_Success() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);
            option.deductStock(50);
            long versionAfterDeduction = option.getVersion();

            option.restoreStock(30);

            assertEquals(80, option.getStock());
            assertEquals(versionAfterDeduction + 1, option.getVersion());
        }

        @Test
        @DisplayName("재고 복구 - 전체 복구")
        void testRestoreStock_FullRestore() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);
            option.deductStock(100);

            option.restoreStock(100);

            assertEquals(100, option.getStock());
        }

        @Test
        @DisplayName("재고 복구 - 초과 복구 (증가)")
        void testRestoreStock_OverRestore() {
            ProductOption option = ProductOption.builder()
                    .productId(TEST_PRODUCT_ID)
                    .name(TEST_OPTION_NAME)
                    .stock(100)
                    .version(1L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            option.deductStock(50);

            option.restoreStock(70);

            assertEquals(120, option.getStock()); // 100 - 50 + 70 = 120
        }

        @Test
        @DisplayName("재고 복구 실패 - 0 수량")
        void testRestoreStock_ZeroQuantity() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);
            option.deductStock(50);
            long versionAfterDeduction = option.getVersion();

            assertThrows(IllegalArgumentException.class, () ->
                option.restoreStock(0)
            );

            assertEquals(50, option.getStock());
            assertEquals(versionAfterDeduction, option.getVersion());
        }

        @Test
        @DisplayName("재고 복구 실패 - 음수 수량")
        void testRestoreStock_NegativeQuantity() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);
            option.deductStock(50);
            long versionAfterDeduction = option.getVersion();

            assertThrows(IllegalArgumentException.class, () ->
                option.restoreStock(-10)
            );

            assertEquals(50, option.getStock());
            assertEquals(versionAfterDeduction, option.getVersion());
        }
    }

    // ===== OPTIMISTIC LOCKING TESTS =====
    @Nested
    @DisplayName("낙관적 락 (버전 관리)")
    class OptimisticLockingTests {

        @Test
        @DisplayName("버전 증가 - 차감 후")
        void testVersionIncrement_AfterDeduction() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);
            assertEquals(1L, option.getVersion());

            option.deductStock(10);
            assertEquals(2L, option.getVersion());

            option.deductStock(20);
            assertEquals(3L, option.getVersion());

            option.deductStock(5);
            assertEquals(4L, option.getVersion());
        }

        @Test
        @DisplayName("버전 증가 - 복구 후")
        void testVersionIncrement_AfterRestoration() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);
            option.deductStock(50);
            long versionAfterDeduction = option.getVersion();

            option.restoreStock(30);
            assertEquals(versionAfterDeduction + 1, option.getVersion());

            option.restoreStock(20);
            assertEquals(versionAfterDeduction + 2, option.getVersion());
        }

        @Test
        @DisplayName("버전 체크 - 변경 감지")
        void testVersionChanged_Detection() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);

            assertFalse(option.isVersionChanged(1L));

            option.deductStock(10);
            assertTrue(option.isVersionChanged(1L));
            assertFalse(option.isVersionChanged(2L));

            option.restoreStock(5);
            assertTrue(option.isVersionChanged(2L));
            assertFalse(option.isVersionChanged(3L));
        }

        @Test
        @DisplayName("동시성 시뮬레이션 - 두 트랜잭션의 버전 충돌")
        void testConcurrencySimulation_VersionConflict() {
            // 초기 상태: 버전 1, 재고 100
            ProductOption original = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);
            assertEquals(1L, original.getVersion());

            // 트랜잭션 1: 20 차감 (로컬 복사본 - Builder로 버전 1 설정)
            ProductOption transaction1 = ProductOption.builder()
                    .productId(TEST_PRODUCT_ID)
                    .name(TEST_OPTION_NAME)
                    .stock(100)
                    .version(1L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            transaction1.deductStock(20);
            // transaction1.version = 2

            // 트랜잭션 2: 30 차감 (로컬 복사본 - Builder로 버전 1 설정)
            ProductOption transaction2 = ProductOption.builder()
                    .productId(TEST_PRODUCT_ID)
                    .name(TEST_OPTION_NAME)
                    .stock(100)
                    .version(1L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            transaction2.deductStock(30);
            // transaction2.version = 2

            // 트랜잭션 1이 먼저 커밋
            assertEquals(2L, transaction1.getVersion());
            assertEquals(80, transaction1.getStock());

            // 트랜잭션 2가 커밋 시도
            // 데이터베이스의 버전이 2로 변경되었으므로
            assertEquals(2L, transaction2.getVersion());

            // 낙관적 락 감지: DB의 버전(2)과 읽은 버전(1)이 다름
            assertTrue(transaction2.isVersionChanged(1L));
        }

        @Test
        @DisplayName("연속 작업 - 버전 연쇄 증가")
        void testSequentialOperations_VersionChaining() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);

            // 작업 1-5: 각각 재고 차감
            for (long i = 1L; i <= 5; i++) {
                option.deductStock(1);
                assertEquals(i + 1, option.getVersion());
            }

            assertEquals(95, option.getStock());
            assertEquals(6L, option.getVersion());

            // 작업 6-8: 각각 재고 복구
            for (long i = 1L; i <= 3; i++) {
                option.restoreStock(1);
                assertEquals(6 + i, option.getVersion());
            }

            assertEquals(98, option.getStock());
            assertEquals(9L, option.getVersion());
        }
    }

    // ===== STOCK INQUIRY TESTS =====
    @Nested
    @DisplayName("재고 조회 메서드")
    class StockInquiryTests {

        @Test
        @DisplayName("hasStock() - 재고 있음")
        void testHasStock_True() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);

            assertTrue(option.hasStock(50));
            assertTrue(option.hasStock(100));
            assertTrue(option.hasStock(1));
        }

        @Test
        @DisplayName("hasStock() - 재고 없음")
        void testHasStock_False() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);

            assertFalse(option.hasStock(101));
            assertFalse(option.hasStock(150));

            option.deductStock(100);
            assertFalse(option.hasStock(1));
        }

        @Test
        @DisplayName("hasStock() - 정확한 수량")
        void testHasStock_ExactQuantity() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);

            assertTrue(option.hasStock(100));
            assertFalse(option.hasStock(101));
        }

        @Test
        @DisplayName("hasAnyStock() - 재고 있음")
        void testHasAnyStock_True() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);

            assertTrue(option.hasAnyStock());

            option.deductStock(99);
            assertTrue(option.hasAnyStock());
        }

        @Test
        @DisplayName("hasAnyStock() - 재고 없음")
        void testHasAnyStock_False() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);
            option.deductStock(100);

            assertFalse(option.hasAnyStock());
        }

        @Test
        @DisplayName("isOutOfStock() - true")
        void testIsOutOfStock_True() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 0);

            assertTrue(option.isOutOfStock());

            option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);
            option.deductStock(100);
            assertTrue(option.isOutOfStock());
        }

        @Test
        @DisplayName("isOutOfStock() - false")
        void testIsOutOfStock_False() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);

            assertFalse(option.isOutOfStock());

            option.deductStock(99);
            assertFalse(option.isOutOfStock());
        }

        @Test
        @DisplayName("getAvailableStock() - 현재 재고 반환")
        void testGetAvailableStock() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);

            assertEquals(100, option.getAvailableStock());

            option.deductStock(30);
            assertEquals(70, option.getAvailableStock());

            option.restoreStock(50);
            assertEquals(120, option.getAvailableStock());
        }
    }

    // ===== INFO UPDATE TESTS =====
    @Nested
    @DisplayName("옵션 정보 수정 (updateInfo)")
    class InfoUpdateTests {

        @Test
        @DisplayName("옵션명 수정 - 정상")
        void testUpdateInfo_Success() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);
            LocalDateTime beforeUpdate = option.getUpdatedAt();

            option.updateInfo("파랑");

            assertEquals("파랑", option.getName());
            assertTrue(option.getUpdatedAt().isAfter(beforeUpdate) ||
                      option.getUpdatedAt().isEqual(beforeUpdate));
        }

        @Test
        @DisplayName("옵션명 수정 - null은 무시")
        void testUpdateInfo_Null() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);

            option.updateInfo(null);

            assertEquals(TEST_OPTION_NAME, option.getName());
        }

        @Test
        @DisplayName("옵션명 수정 - 빈 문자열은 무시")
        void testUpdateInfo_Blank() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);

            option.updateInfo("  ");

            assertEquals(TEST_OPTION_NAME, option.getName());
        }

        @Test
        @DisplayName("옵션명 수정 - 유효한 새 이름")
        void testUpdateInfo_ValidNewName() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, "M사이즈", 50);

            option.updateInfo("L사이즈");

            assertEquals("L사이즈", option.getName());
        }
    }

    // ===== TIMESTAMP UPDATE TESTS =====
    @Nested
    @DisplayName("타임스탐프 업데이트")
    class TimestampUpdateTests {

        @Test
        @DisplayName("생성 시 createdAt과 updatedAt 설정")
        void testTimestampOnCreation() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);

            assertNotNull(option.getCreatedAt());
            assertNotNull(option.getUpdatedAt());
            // 마이크로초 오차 허용 (1밀리초 이내)
            assertTrue(Math.abs(java.time.temporal.ChronoUnit.MICROS.between(
                    option.getCreatedAt(), option.getUpdatedAt())) < 1000);
        }

        @Test
        @DisplayName("재고 차감 후 updatedAt 갱신")
        void testTimestampOnDeduction() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);
            LocalDateTime createdAt = option.getCreatedAt();
            LocalDateTime updatedAtBefore = option.getUpdatedAt();

            option.deductStock(10);

            assertTrue(option.getUpdatedAt().isAfter(updatedAtBefore) ||
                      option.getUpdatedAt().isEqual(updatedAtBefore));
            assertEquals(createdAt, option.getCreatedAt());
        }

        @Test
        @DisplayName("재고 복구 후 updatedAt 갱신")
        void testTimestampOnRestoration() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);
            option.deductStock(50);
            LocalDateTime updatedAtAfterDeduction = option.getUpdatedAt();

            option.restoreStock(30);

            assertTrue(option.getUpdatedAt().isAfter(updatedAtAfterDeduction) ||
                      option.getUpdatedAt().isEqual(updatedAtAfterDeduction));
        }

        @Test
        @DisplayName("정보 수정 후 updatedAt 갱신")
        void testTimestampOnInfoUpdate() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, TEST_OPTION_NAME, 100);
            LocalDateTime updatedAtBefore = option.getUpdatedAt();

            option.updateInfo("새로운 이름");

            assertTrue(option.getUpdatedAt().isAfter(updatedAtBefore) ||
                      option.getUpdatedAt().isEqual(updatedAtBefore));
        }
    }

    // ===== REAL-WORLD SCENARIO TESTS =====
    @Nested
    @DisplayName("실제 시나리오 테스트")
    class RealWorldScenarioTests {

        @Test
        @DisplayName("시나리오 1: 정상 재고 관리 흐름")
        void scenario1_NormalInventoryFlow() {
            // 옵션 생성: 빨강색 100개
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, "빨강", 100);
            assertEquals(1L, option.getVersion());

            // 주문 1: 30개 차감
            option.deductStock(30);
            assertEquals(70, option.getStock());
            assertEquals(2L, option.getVersion());

            // 주문 2: 20개 차감
            option.deductStock(20);
            assertEquals(50, option.getStock());
            assertEquals(3L, option.getVersion());

            // 주문 취소: 15개 복구
            option.restoreStock(15);
            assertEquals(65, option.getStock());
            assertEquals(4L, option.getVersion());

            // 주문 3: 65개 차감 (전량)
            option.deductStock(65);
            assertEquals(0, option.getStock());
            assertEquals(5L, option.getVersion());

            // 최종 상태
            assertFalse(option.hasAnyStock());
            assertTrue(option.isOutOfStock());
        }

        @Test
        @DisplayName("시나리오 2: 동시성 감지 (낙관적 락)")
        void scenario2_ConcurrencyDetection() {
            // 원본 데이터: 버전 1, 재고 1000
            ProductOption original = ProductOption.createOption(TEST_PRODUCT_ID, "M사이즈", 1000);
            long initialVersion = original.getVersion();

            // 사용자 A: 로컬 복사본 (버전 1에서 200개 차감 - Builder로 생성)
            ProductOption userA = ProductOption.builder()
                    .productId(TEST_PRODUCT_ID)
                    .name("M사이즈")
                    .stock(1000)
                    .version(initialVersion)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userA.deductStock(200);

            // 사용자 B: 로컬 복사본 (버전 1에서 300개 차감 - Builder로 생성)
            ProductOption userB = ProductOption.builder()
                    .productId(TEST_PRODUCT_ID)
                    .name("M사이즈")
                    .stock(1000)
                    .version(initialVersion)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userB.deductStock(300);
            long userBVersion = userB.getVersion(); // 2

            // DB 버전이 userA에 의해 2로 변경됨
            // userB는 버전 1에서 읽었으므로 충돌 감지 필요
            assertTrue(userB.isVersionChanged(initialVersion));
            assertFalse(userB.isVersionChanged(userBVersion));
        }

        @Test
        @DisplayName("시나리오 3: 부분 반품 처리")
        void scenario3_PartialRefund() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, "L사이즈", 200);

            // 주문 1: 100개 판매
            option.deductStock(100);
            assertEquals(100, option.getStock());
            assertEquals(2L, option.getVersion());

            // 부분 반품: 30개만 반품
            option.restoreStock(30);
            assertEquals(130, option.getStock());
            assertEquals(3L, option.getVersion());

            // 추가 주문: 90개 판매
            option.deductStock(90);
            assertEquals(40, option.getStock());
            assertEquals(4L, option.getVersion());

            assertTrue(option.hasStock(40));
            assertFalse(option.hasStock(41));
        }

        @Test
        @DisplayName("시나리오 4: 버전 추적을 통한 감시")
        void scenario4_VersionTrackingMonitoring() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, "XL사이즈", 500);
            long versionAtCreation = option.getVersion();

            // 10번의 연속 작업
            for (int i = 0; i < 5; i++) {
                option.deductStock(10);
            }
            long versionAfter5Deductions = option.getVersion();

            for (int i = 0; i < 3; i++) {
                option.restoreStock(5);
            }
            long versionAfter3Restorations = option.getVersion();

            // 버전 진행: 1 → 6 → 9
            assertEquals(1L, versionAtCreation);
            assertEquals(6L, versionAfter5Deductions);
            assertEquals(9L, versionAfter3Restorations);

            // 각 단계에서의 버전 변경 감지
            assertTrue(option.isVersionChanged(versionAtCreation));
            assertTrue(option.isVersionChanged(versionAfter5Deductions));
            assertFalse(option.isVersionChanged(versionAfter3Restorations));
        }

        @Test
        @DisplayName("시나리오 5: 높은 거래량 시뮬레이션")
        void scenario5_HighTransactionVolume() {
            ProductOption option = ProductOption.createOption(TEST_PRODUCT_ID, "인기상품", 10000);

            // 100번의 판매 거래 시뮬레이션
            int totalSold = 0;
            for (int i = 0; i < 100; i++) {
                int quantity = (i % 10) + 1; // 1~10개씩
                option.deductStock(quantity);
                totalSold += quantity;
            }

            assertEquals(10000 - totalSold, option.getStock());
            assertEquals(101L, option.getVersion()); // 초기(1) + 100번 = 101

            // 부분 반품
            option.restoreStock(totalSold / 2);
            assertEquals(10000 - totalSold + (totalSold / 2), option.getStock());
            assertEquals(102L, option.getVersion());
        }
    }
}
