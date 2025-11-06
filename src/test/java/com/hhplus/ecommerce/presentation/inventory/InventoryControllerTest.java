package com.hhplus.ecommerce.presentation.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.application.inventory.InventoryService;
import com.hhplus.ecommerce.presentation.inventory.InventoryController;
import com.hhplus.ecommerce.presentation.inventory.response.InventoryResponse;
import com.hhplus.ecommerce.presentation.inventory.response.OptionInventoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * InventoryControllerTest - Presentation Layer Unit Test
 * Spring Boot 3.4+ Mockito 방식 테스트
 *
 * 테스트 대상: InventoryController
 * - GET /inventory/{product_id} - 상품 재고 현황 조회
 *
 * 테스트 유형:
 * - 성공 케이스: 정상적인 재고 조회
 * - 옵션 있음/없음: 상품 옵션 유무에 따른 조회
 * - 실패 케이스: 유효하지 않은 product_id
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryController 단위 테스트")
class InventoryControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private InventoryController inventoryController;

    private static final Long TEST_PRODUCT_ID = 1L;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        this.mockMvc = MockMvcBuilders.standaloneSetup(inventoryController).build();
        this.objectMapper = new ObjectMapper();
    }

    // ========== 상품 재고 조회 (GET /inventory/{product_id}) ==========

    @Test
    @DisplayName("상품 재고 조회 - 성공 (옵션 있음)")
    void testGetProductInventory_Success_WithOptions() throws Exception {
        // Given
        InventoryResponse response = InventoryResponse.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("프리미엄 우육 500g")
                .totalStock(450)
                .options(Arrays.asList(
                        OptionInventoryView.builder()
                                .optionId(1L)
                                .name("사이즈 S")
                                .stock(100)
                                .version(1L)
                                .build(),
                        OptionInventoryView.builder()
                                .optionId(2L)
                                .name("사이즈 M")
                                .stock(150)
                                .version(1L)
                                .build(),
                        OptionInventoryView.builder()
                                .optionId(3L)
                                .name("사이즈 L")
                                .stock(200)
                                .version(1L)
                                .build()
                ))
                .build();

        when(inventoryService.getProductInventory(TEST_PRODUCT_ID))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/inventory/{product_id}", TEST_PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_id").value(TEST_PRODUCT_ID))
                .andExpect(jsonPath("$.product_name").value("프리미엄 우육 500g"))
                .andExpect(jsonPath("$.total_stock").value(450))
                .andExpect(jsonPath("$.options").isArray())
                .andExpect(jsonPath("$.options", hasSize(3)))
                .andExpect(jsonPath("$.options[0].name").value("사이즈 S"))
                .andExpect(jsonPath("$.options[0].stock").value(100));

        verify(inventoryService, times(1)).getProductInventory(TEST_PRODUCT_ID);
    }

    @Test
    @DisplayName("상품 재고 조회 - 성공 (단일 옵션)")
    void testGetProductInventory_Success_SingleOption() throws Exception {
        // Given
        InventoryResponse response = InventoryResponse.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("기본 상품")
                .totalStock(500)
                .options(Collections.singletonList(
                        OptionInventoryView.builder()
                                .optionId(1L)
                                .name("기본")
                                .stock(500)
                                .version(1L)
                                .build()
                ))
                .build();

        when(inventoryService.getProductInventory(TEST_PRODUCT_ID))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/inventory/{product_id}", TEST_PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_stock").value(500))
                .andExpect(jsonPath("$.options", hasSize(1)));

        verify(inventoryService, times(1)).getProductInventory(TEST_PRODUCT_ID);
    }

    @Test
    @DisplayName("상품 재고 조회 - 성공 (재고 0)")
    void testGetProductInventory_Success_ZeroStock() throws Exception {
        // Given
        InventoryResponse response = InventoryResponse.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("품절 상품")
                .totalStock(0)
                .options(Collections.singletonList(
                        OptionInventoryView.builder()
                                .optionId(1L)
                                .name("기본")
                                .stock(0)
                                .version(1L)
                                .build()
                ))
                .build();

        when(inventoryService.getProductInventory(TEST_PRODUCT_ID))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/inventory/{product_id}", TEST_PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_stock").value(0));

        verify(inventoryService, times(1)).getProductInventory(TEST_PRODUCT_ID);
    }

    @Test
    @DisplayName("상품 재고 조회 - 성공 (많은 옵션)")
    void testGetProductInventory_Success_ManyOptions() throws Exception {
        // Given
        InventoryResponse response = InventoryResponse.builder()
                .productId(TEST_PRODUCT_ID)
                .productName("다양한 옵션 상품")
                .totalStock(1000)
                .options(Arrays.asList(
                        OptionInventoryView.builder()
                                .optionId(1L)
                                .name("색상 빨강")
                                .stock(100)
                                .version(1L)
                                .build(),
                        OptionInventoryView.builder()
                                .optionId(2L)
                                .name("색상 파랑")
                                .stock(150)
                                .version(1L)
                                .build(),
                        OptionInventoryView.builder()
                                .optionId(3L)
                                .name("색상 초록")
                                .stock(200)
                                .version(1L)
                                .build(),
                        OptionInventoryView.builder()
                                .optionId(4L)
                                .name("색상 노랑")
                                .stock(250)
                                .version(1L)
                                .build(),
                        OptionInventoryView.builder()
                                .optionId(5L)
                                .name("색상 검정")
                                .stock(300)
                                .version(1L)
                                .build()
                ))
                .build();

        when(inventoryService.getProductInventory(TEST_PRODUCT_ID))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/inventory/{product_id}", TEST_PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options", hasSize(5)))
                .andExpect(jsonPath("$.total_stock").value(1000));

        verify(inventoryService, times(1)).getProductInventory(TEST_PRODUCT_ID);
    }

    @Test
    @DisplayName("상품 재고 조회 - 실패 (유효하지 않은 product_id 타입)")
    void testGetProductInventory_Failed_InvalidProductIdType() throws Exception {
        // When & Then
        mockMvc.perform(get("/inventory/{product_id}", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상품 재고 조회 - 실패 (음수 product_id)")
    void testGetProductInventory_Failed_NegativeProductId() throws Exception {
        // When & Then
        mockMvc.perform(get("/inventory/{product_id}", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상품 재고 조회 - 실패 (0 product_id)")
    void testGetProductInventory_Failed_ZeroProductId() throws Exception {
        // When & Then
        mockMvc.perform(get("/inventory/{product_id}", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상품 재고 조회 - 실패 (매우 큰 product_id)")
    void testGetProductInventory_Failed_VeryLargeProductId() throws Exception {
        // When & Then
        mockMvc.perform(get("/inventory/{product_id}", "9999999999999999999"))
                .andExpect(status().isBadRequest());
    }

    // ========== 응답 포맷 검증 ==========

    @Test
    @DisplayName("상품 재고 조회 - 응답 필드 검증")
    void testGetProductInventory_ResponseFieldValidation() throws Exception {
        // Given
        when(inventoryService.getProductInventory(anyLong()))
                .thenReturn(InventoryResponse.builder()
                        .productId(TEST_PRODUCT_ID)
                        .productName("테스트 상품")
                        .totalStock(100)
                        .options(Collections.singletonList(
                                OptionInventoryView.builder()
                                        .optionId(1L)
                                        .name("테스트 옵션")
                                        .stock(100)
                                        .version(1L)
                                        .build()
                        ))
                        .build());

        // When & Then
        mockMvc.perform(get("/inventory/{product_id}", TEST_PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap())
                .andExpect(jsonPath("$.product_id").isNumber())
                .andExpect(jsonPath("$.product_name").isString())
                .andExpect(jsonPath("$.total_stock").isNumber())
                .andExpect(jsonPath("$.options").isArray());
    }

    @Test
    @DisplayName("상품 재고 조회 - 옵션 응답 필드 검증")
    void testGetProductInventory_OptionResponseFieldValidation() throws Exception {
        // Given
        when(inventoryService.getProductInventory(anyLong()))
                .thenReturn(InventoryResponse.builder()
                        .productId(TEST_PRODUCT_ID)
                        .productName("테스트 상품")
                        .totalStock(200)
                        .options(Collections.singletonList(
                                OptionInventoryView.builder()
                                        .optionId(1L)
                                        .name("테스트 옵션")
                                        .stock(200)
                                        .version(1L)
                                        .build()
                        ))
                        .build());

        // When & Then
        mockMvc.perform(get("/inventory/{product_id}", TEST_PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0]").isMap())
                .andExpect(jsonPath("$.options[0].option_id").isNumber())
                .andExpect(jsonPath("$.options[0].name").isString())
                .andExpect(jsonPath("$.options[0].stock").isNumber())
                .andExpect(jsonPath("$.options[0].version").isNumber());
    }

    @Test
    @DisplayName("상품 재고 조회 - 재고 계산 검증")
    void testGetProductInventory_StockCalculationValidation() throws Exception {
        // Given - 옵션별 재고 합이 총 재고와 일치하는지 검증
        when(inventoryService.getProductInventory(TEST_PRODUCT_ID))
                .thenReturn(InventoryResponse.builder()
                        .productId(TEST_PRODUCT_ID)
                        .productName("재고 계산 테스트")
                        .totalStock(300)  // 100 + 100 + 100 = 300
                        .options(Arrays.asList(
                                OptionInventoryView.builder()
                                        .optionId(1L)
                                        .name("옵션 1")
                                        .stock(100)
                                        .version(1L)
                                        .build(),
                                OptionInventoryView.builder()
                                        .optionId(2L)
                                        .name("옵션 2")
                                        .stock(100)
                                        .version(1L)
                                        .build(),
                                OptionInventoryView.builder()
                                        .optionId(3L)
                                        .name("옵션 3")
                                        .stock(100)
                                        .version(1L)
                                        .build()
                        ))
                        .build());

        // When & Then
        mockMvc.perform(get("/inventory/{product_id}", TEST_PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_stock").value(300))
                .andExpect(jsonPath("$.options", hasSize(3)))
                .andExpect(jsonPath("$.options[0].stock").value(100))
                .andExpect(jsonPath("$.options[1].stock").value(100))
                .andExpect(jsonPath("$.options[2].stock").value(100));

        verify(inventoryService, times(1)).getProductInventory(TEST_PRODUCT_ID);
    }
}
