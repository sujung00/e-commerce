package com.hhplus.ecommerce.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProductStatus 열거형 단위 테스트
 * - 열거형 값 확인
 * - 문자열로부터 상태 변환 (from 메서드)
 * - 화면 표시명 관리
 * - 기본값 처리
 */
@DisplayName("ProductStatus 열거형 테스트")
class ProductStatusTest {

    // ========== ProductStatus 값 확인 ==========

    @Test
    @DisplayName("ProductStatus 값 - 판매중")
    void testProductStatusValue_Available() {
        // When
        ProductStatus status = ProductStatus.판매중;

        // Then
        assertNotNull(status);
        assertEquals(ProductStatus.판매중, status);
    }

    @Test
    @DisplayName("ProductStatus 값 - 품절")
    void testProductStatusValue_SoldOut() {
        // When
        ProductStatus status = ProductStatus.품절;

        // Then
        assertNotNull(status);
        assertEquals(ProductStatus.품절, status);
    }

    @Test
    @DisplayName("ProductStatus 값 - 판매중지")
    void testProductStatusValue_Discontinued() {
        // When
        ProductStatus status = ProductStatus.판매중지;

        // Then
        assertNotNull(status);
        assertEquals(ProductStatus.판매중지, status);
    }

    // ========== ProductStatus 열거형 모든 값 ==========

    @Test
    @DisplayName("ProductStatus 모든 값 확인")
    void testProductStatus_AllValues() {
        // When
        ProductStatus[] statuses = ProductStatus.values();

        // Then
        assertEquals(3, statuses.length);
        assertTrue(contains(statuses, ProductStatus.판매중));
        assertTrue(contains(statuses, ProductStatus.품절));
        assertTrue(contains(statuses, ProductStatus.판매중지));
    }

    private boolean contains(ProductStatus[] statuses, ProductStatus status) {
        for (ProductStatus s : statuses) {
            if (s == status) {
                return true;
            }
        }
        return false;
    }

    // ========== 화면 표시명 관리 ==========

    @Test
    @DisplayName("화면 표시명 - 판매중")
    void testDisplayName_Available() {
        // When
        String displayName = ProductStatus.판매중.getDisplayName();

        // Then
        assertEquals("판매 중", displayName);
    }

    @Test
    @DisplayName("화면 표시명 - 품절")
    void testDisplayName_SoldOut() {
        // When
        String displayName = ProductStatus.품절.getDisplayName();

        // Then
        assertEquals("품절", displayName);
    }

    @Test
    @DisplayName("화면 표시명 - 판매중지")
    void testDisplayName_Discontinued() {
        // When
        String displayName = ProductStatus.판매중지.getDisplayName();

        // Then
        assertEquals("판매 중지", displayName);
    }

    // ========== from() 메서드 - 정상 경로 ==========

    @Test
    @DisplayName("from() - 판매 중으로 변환")
    void testFrom_AvailableDisplayName() {
        // When
        ProductStatus status = ProductStatus.from("판매 중");

        // Then
        assertEquals(ProductStatus.판매중, status);
    }

    @Test
    @DisplayName("from() - 품절로 변환")
    void testFrom_SoldOutDisplayName() {
        // When
        ProductStatus status = ProductStatus.from("품절");

        // Then
        assertEquals(ProductStatus.품절, status);
    }

    @Test
    @DisplayName("from() - 판매 중지로 변환")
    void testFrom_DiscontinuedDisplayName() {
        // When
        ProductStatus status = ProductStatus.from("판매 중지");

        // Then
        assertEquals(ProductStatus.판매중지, status);
    }

    // ========== from() 메서드 - 기본값 처리 ==========

    @Test
    @DisplayName("from() - 유효하지 않은 값은 판매중으로 기본값")
    void testFrom_InvalidValue_DefaultToAvailable() {
        // When
        ProductStatus status = ProductStatus.from("유효하지 않은 상태");

        // Then
        assertEquals(ProductStatus.판매중, status);
    }

    @Test
    @DisplayName("from() - null 입력은 판매중으로 기본값")
    void testFrom_NullValue_DefaultToAvailable() {
        // When: null 입력 처리
        ProductStatus status = ProductStatus.from(null);

        // Then: 기본값으로 판매중 반환
        assertEquals(ProductStatus.판매중, status);
    }

    @Test
    @DisplayName("from() - 빈 문자열은 판매중으로 기본값")
    void testFrom_EmptyString_DefaultToAvailable() {
        // When
        ProductStatus status = ProductStatus.from("");

        // Then
        assertEquals(ProductStatus.판매중, status);
    }

    @Test
    @DisplayName("from() - 공백만 있는 문자열은 판매중으로 기본값")
    void testFrom_WhitespaceOnly_DefaultToAvailable() {
        // When
        ProductStatus status = ProductStatus.from("   ");

        // Then
        assertEquals(ProductStatus.판매중, status);
    }

    @Test
    @DisplayName("from() - 잘못된 대소문자도 판매중으로 기본값")
    void testFrom_WrongCase_DefaultToAvailable() {
        // When
        ProductStatus status = ProductStatus.from("판매중");  // 다른 형식

        // Then
        assertEquals(ProductStatus.판매중, status);  // 기본값
    }

    // ========== from() 메서드 - 다양한 입력값 ==========

    @Test
    @DisplayName("from() - 다양한 유효하지 않은 입력값 테스트")
    void testFrom_VariousInvalidValues() {
        // When/Then
        assertEquals(ProductStatus.판매중, ProductStatus.from("판매"));
        assertEquals(ProductStatus.판매중, ProductStatus.from("sold out"));
        assertEquals(ProductStatus.판매중, ProductStatus.from("SOLD_OUT"));
        assertEquals(ProductStatus.판매중, ProductStatus.from("Available"));
        assertEquals(ProductStatus.판매중, ProductStatus.from("123"));
    }

    // ========== 상태 비교 및 동등성 ==========

    @Test
    @DisplayName("상태 비교 - 동일 상태")
    void testStatusEquality_SameStatus() {
        // When
        ProductStatus status1 = ProductStatus.판매중;
        ProductStatus status2 = ProductStatus.판매중;

        // Then
        assertEquals(status1, status2);
    }

    @Test
    @DisplayName("상태 비교 - 다른 상태")
    void testStatusEquality_DifferentStatus() {
        // When
        ProductStatus status1 = ProductStatus.판매중;
        ProductStatus status2 = ProductStatus.품절;

        // Then
        assertNotEquals(status1, status2);
    }

    @Test
    @DisplayName("상태 비교 - from()으로 변환된 값과 직접 값 비교")
    void testStatusEquality_FromVsDirect() {
        // When
        ProductStatus directStatus = ProductStatus.판매중;
        ProductStatus fromStatus = ProductStatus.from("판매 중");

        // Then
        assertEquals(directStatus, fromStatus);
    }

    // ========== 상태 전이 시나리오 ==========

    @Test
    @DisplayName("상태 전이 - 판매중 → 품절")
    void testStateTransition_AvailableToSoldOut() {
        // When
        ProductStatus currentStatus = ProductStatus.판매중;
        ProductStatus newStatus = ProductStatus.품절;

        // Then
        assertNotEquals(currentStatus, newStatus);
        assertEquals(ProductStatus.품절, newStatus);
    }

    @Test
    @DisplayName("상태 전이 - 판매중 → 판매중지")
    void testStateTransition_AvailableToDiscontinued() {
        // When
        ProductStatus currentStatus = ProductStatus.판매중;
        ProductStatus newStatus = ProductStatus.판매중지;

        // Then
        assertNotEquals(currentStatus, newStatus);
        assertEquals(ProductStatus.판매중지, newStatus);
    }

    @Test
    @DisplayName("상태 전이 - 품절 → 판매중")
    void testStateTransition_SoldOutToAvailable() {
        // When
        ProductStatus currentStatus = ProductStatus.품절;
        ProductStatus newStatus = ProductStatus.판매중;

        // Then
        assertNotEquals(currentStatus, newStatus);
        assertEquals(ProductStatus.판매중, newStatus);
    }

    @Test
    @DisplayName("상태 전이 - 판매중지 → 판매중")
    void testStateTransition_DiscontinuedToAvailable() {
        // When
        ProductStatus currentStatus = ProductStatus.판매중지;
        ProductStatus newStatus = ProductStatus.판매중;

        // Then
        assertNotEquals(currentStatus, newStatus);
        assertEquals(ProductStatus.판매중, newStatus);
    }

    // ========== 열거형 메서드 ==========

    @Test
    @DisplayName("valueOf() - 문자열로 열거형 조회")
    void testValueOf_GetEnumByName() {
        // When
        ProductStatus status = ProductStatus.valueOf("판매중");

        // Then
        assertEquals(ProductStatus.판매중, status);
    }

    @Test
    @DisplayName("name() - 열거형의 문자 이름")
    void testName_GetEnumName() {
        // When
        String name = ProductStatus.판매중.name();

        // Then
        assertEquals("판매중", name);
    }

    @Test
    @DisplayName("ordinal() - 열거형의 순서값")
    void testOrdinal_GetEnumPosition() {
        // When
        int ordinal1 = ProductStatus.판매중.ordinal();
        int ordinal2 = ProductStatus.품절.ordinal();
        int ordinal3 = ProductStatus.판매중지.ordinal();

        // Then
        assertEquals(0, ordinal1);
        assertEquals(1, ordinal2);
        assertEquals(2, ordinal3);
    }

    // ========== null 안전성 ==========

    @Test
    @DisplayName("null 안전성 - from() 메서드 null 입력")
    void testNullSafety_FromMethod() {
        // When/Then
        assertDoesNotThrow(() -> {
            ProductStatus status = ProductStatus.from(null);
            assertEquals(ProductStatus.판매중, status);  // 기본값
        });
    }

    @Test
    @DisplayName("null 안전성 - displayName 비교")
    void testNullSafety_DisplayName() {
        // When
        ProductStatus status = ProductStatus.판매중;
        String displayName = status.getDisplayName();

        // Then
        assertNotNull(displayName);
        assertFalse(displayName.isEmpty());
    }

    // ========== 실제 사용 시나리오 ==========

    @Test
    @DisplayName("사용 시나리오 - 상태 표시")
    void testScenario_DisplayStatus() {
        // When
        ProductStatus status = ProductStatus.판매중;
        String displayText = status.getDisplayName();

        // Then
        assertEquals("판매 중", displayText);
    }

    @Test
    @DisplayName("사용 시나리오 - API 응답에서 상태 변환")
    void testScenario_ConvertFromApiResponse() {
        // When
        String apiResponse = "판매 중";
        ProductStatus status = ProductStatus.from(apiResponse);

        // Then
        assertEquals(ProductStatus.판매중, status);
    }

    @Test
    @DisplayName("사용 시나리오 - 상태별 조건 분기")
    void testScenario_ConditionalBranching() {
        // When
        ProductStatus status = ProductStatus.판매중;

        // Then
        if (status == ProductStatus.판매중) {
            assertTrue(true);  // 판매 가능
        } else if (status == ProductStatus.품절) {
            fail("품절 상태가 아님");
        } else if (status == ProductStatus.판매중지) {
            fail("판매중지 상태가 아님");
        }
    }

    @Test
    @DisplayName("사용 시나리오 - 모든 상태에 대한 표시명 확인")
    void testScenario_AllDisplayNames() {
        // When/Then
        for (ProductStatus status : ProductStatus.values()) {
            String displayName = status.getDisplayName();
            assertNotNull(displayName);
            assertFalse(displayName.isEmpty());
        }
    }

    @Test
    @DisplayName("사용 시나리오 - 상태 표시명 매핑")
    void testScenario_StatusMapping() {
        // When
        ProductStatus status1 = ProductStatus.from("판매 중");
        ProductStatus status2 = ProductStatus.from("품절");
        ProductStatus status3 = ProductStatus.from("판매 중지");

        // Then
        assertEquals("판매 중", status1.getDisplayName());
        assertEquals("품절", status2.getDisplayName());
        assertEquals("판매 중지", status3.getDisplayName());
    }
}
