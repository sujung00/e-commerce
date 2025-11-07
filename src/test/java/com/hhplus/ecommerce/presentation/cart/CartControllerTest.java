package com.hhplus.ecommerce.presentation.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.application.cart.CartService;
import com.hhplus.ecommerce.presentation.cart.request.AddCartItemRequest;
import com.hhplus.ecommerce.presentation.cart.request.UpdateQuantityRequest;
import com.hhplus.ecommerce.presentation.cart.response.CartItemResponse;
import com.hhplus.ecommerce.presentation.cart.response.CartResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.hhplus.ecommerce.common.BaseControllerTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CartControllerTest - Presentation Layer Unit Test
 * Spring Boot 3.4+ Mockito 방식 테스트
 *
 * 테스트 대상: CartController
 * - GET /carts - 장바구니 조회
 * - POST /carts/items - 장바구니 아이템 추가
 * - PUT /carts/items/{cart_item_id} - 장바구니 아이템 수량 수정
 * - DELETE /carts/items/{cart_item_id} - 장바구니 아이템 제거
 *
 * 테스트 유형:
 * - 성공 케이스: 정상적인 요청 및 응답
 * - 실패 케이스: 잘못된 헤더, 유효성 검사 오류
 * - 경계값 테스트: 최소/최대값, 특수한 경우
 */
@ExtendWith(MockitoExtension.class)
@TestPropertySource(properties = {"spring.web.resources.add-mappings=false"})
@DisplayName("CartController 단위 테스트")
class CartControllerTest extends BaseControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private CartService cartService;

    @InjectMocks
    private CartController cartController;

    private static final Long TEST_USER_ID = 1001L;
    private static final Long TEST_CART_ID = 1L;
    private static final Long TEST_PRODUCT_ID = 100L;
    private static final Long TEST_OPTION_ID = 1001L;
    private static final Long TEST_CART_ITEM_ID = 50L;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        this.mockMvc = MockMvcBuilders.standaloneSetup(cartController).build();
        this.objectMapper = new ObjectMapper();
    }

    // ========== 장바구니 조회 (GET /carts) ==========

    @Test
    @DisplayName("장바구니 조회 - 성공")
    void testGetCart_Success() throws Exception {
        // Given
        CartResponseDto cartResponse = CartResponseDto.builder()
                .cartId(TEST_CART_ID)
                .userId(TEST_USER_ID)
                .totalItems(3)
                .totalPrice(150000L)
                .items(new ArrayList<>())
                .updatedAt(LocalDateTime.now())
                .build();

        when(cartService.getCartByUserId(TEST_USER_ID)).thenReturn(cartResponse);

        // When & Then
        mockMvc.perform(get("/carts")
                .header("X-USER-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cart_id").value(TEST_CART_ID))
                .andExpect(jsonPath("$.user_id").value(TEST_USER_ID))
                .andExpect(jsonPath("$.total_items").value(3))
                .andExpect(jsonPath("$.total_price").value(150000L));

        verify(cartService, times(1)).getCartByUserId(TEST_USER_ID);
    }

    @Test
    @DisplayName("장바구니 조회 - 빈 장바구니")
    void testGetCart_Success_EmptyCart() throws Exception {
        // Given
        CartResponseDto emptyCart = CartResponseDto.builder()
                .cartId(TEST_CART_ID)
                .userId(TEST_USER_ID)
                .totalItems(0)
                .totalPrice(0L)
                .items(new ArrayList<>())
                .updatedAt(LocalDateTime.now())
                .build();

        when(cartService.getCartByUserId(TEST_USER_ID)).thenReturn(emptyCart);

        // When & Then
        mockMvc.perform(get("/carts")
                .header("X-USER-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_items").value(0))
                .andExpect(jsonPath("$.total_price").value(0L));
    }

    @Test
    @DisplayName("장바구니 조회 - 헤더 누락")
    void testGetCart_Failed_MissingHeader() throws Exception {
        // When & Then
        mockMvc.perform(get("/carts"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장바구니 조회 - 유효하지 않은 사용자 ID")
    void testGetCart_Failed_InvalidUserId() throws Exception {
        // When & Then
        mockMvc.perform(get("/carts")
                .header("X-USER-ID", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장바구니 조회 - 음수 사용자 ID")
    void testGetCart_Failed_NegativeUserId() throws Exception {
        // When & Then
        mockMvc.perform(get("/carts")
                .header("X-USER-ID", "-1"))
                .andExpect(status().isBadRequest());
    }

    // ========== 장바구니 아이템 추가 (POST /carts/items) ==========

    @Test
    @DisplayName("장바구니 아이템 추가 - 성공")
    void testAddCartItem_Success() throws Exception {
        // Given
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(2)
                .build();

        CartItemResponse itemResponse = CartItemResponse.builder()
                .cartItemId(TEST_CART_ITEM_ID)
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(2)
                .unitPrice(50000L)
                .build();

        when(cartService.addItem(eq(TEST_USER_ID), any(AddCartItemRequest.class)))
                .thenReturn(itemResponse);

        // When & Then
        mockMvc.perform(post("/carts/items")
                .header("X-USER-ID", TEST_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cart_item_id").value(TEST_CART_ITEM_ID))
                .andExpect(jsonPath("$.quantity").value(2));

        verify(cartService, times(1)).addItem(eq(TEST_USER_ID), any(AddCartItemRequest.class));
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 최대 수량")
    void testAddCartItem_Success_MaxQuantity() throws Exception {
        // Given
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(1000)
                .build();

        CartItemResponse itemResponse = CartItemResponse.builder()
                .cartItemId(TEST_CART_ITEM_ID)
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(1000)
                .unitPrice(50000L)
                .subtotal(50000000L)
                .build();

        when(cartService.addItem(eq(TEST_USER_ID), any(AddCartItemRequest.class)))
                .thenReturn(itemResponse);

        // When & Then
        mockMvc.perform(post("/carts/items")
                .header("X-USER-ID", TEST_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 요청 바디 누락")
    void testAddCartItem_Failed_MissingBody() throws Exception {
        // When & Then
        mockMvc.perform(post("/carts/items")
                .header("X-USER-ID", TEST_USER_ID)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 수량 0")
    void testAddCartItem_Failed_ZeroQuantity() throws Exception {
        // Given
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(0)
                .build();

        // When & Then
        mockMvc.perform(post("/carts/items")
                .header("X-USER-ID", TEST_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 음수 수량")
    void testAddCartItem_Failed_NegativeQuantity() throws Exception {
        // Given
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(-5)
                .build();

        // When & Then
        mockMvc.perform(post("/carts/items")
                .header("X-USER-ID", TEST_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ========== 장바구니 아이템 수량 수정 (PUT /carts/items/{cart_item_id}) ==========

    @Test
    @DisplayName("장바구니 아이템 수량 수정 - 성공")
    void testUpdateCartItemQuantity_Success() throws Exception {
        // Given
        UpdateQuantityRequest request = UpdateQuantityRequest.builder()
                .quantity(5)
                .build();

        CartItemResponse itemResponse = CartItemResponse.builder()
                .cartItemId(TEST_CART_ITEM_ID)
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(5)
                .unitPrice(50000L)
                .subtotal(250000L)
                .build();

        when(cartService.updateItemQuantity(eq(TEST_USER_ID), eq(TEST_CART_ITEM_ID), any(UpdateQuantityRequest.class)))
                .thenReturn(itemResponse);

        // When & Then
        mockMvc.perform(put("/carts/items/{cart_item_id}", TEST_CART_ITEM_ID)
                .header("X-USER-ID", TEST_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(5));

        verify(cartService, times(1)).updateItemQuantity(
                eq(TEST_USER_ID), eq(TEST_CART_ITEM_ID), any(UpdateQuantityRequest.class));
    }

    @Test
    @DisplayName("장바구니 아이템 수량 수정 - 최소값 (1)")
    void testUpdateCartItemQuantity_Success_MinimumQuantity() throws Exception {
        // Given
        UpdateQuantityRequest request = UpdateQuantityRequest.builder()
                .quantity(1)
                .build();

        CartItemResponse itemResponse = CartItemResponse.builder()
                .cartItemId(TEST_CART_ITEM_ID)
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(1)
                .unitPrice(50000L)
                .subtotal(50000L)
                .build();

        when(cartService.updateItemQuantity(eq(TEST_USER_ID), eq(TEST_CART_ITEM_ID), any(UpdateQuantityRequest.class)))
                .thenReturn(itemResponse);

        // When & Then
        mockMvc.perform(put("/carts/items/{cart_item_id}", TEST_CART_ITEM_ID)
                .header("X-USER-ID", TEST_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(1));
    }

    @Test
    @DisplayName("장바구니 아이템 수량 수정 - 수량 0")
    void testUpdateCartItemQuantity_Failed_ZeroQuantity() throws Exception {
        // Given
        UpdateQuantityRequest request = UpdateQuantityRequest.builder()
                .quantity(0)
                .build();

        // When & Then
        mockMvc.perform(put("/carts/items/{cart_item_id}", TEST_CART_ITEM_ID)
                .header("X-USER-ID", TEST_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장바구니 아이템 수량 수정 - 음수 수량")
    void testUpdateCartItemQuantity_Failed_NegativeQuantity() throws Exception {
        // Given
        UpdateQuantityRequest request = UpdateQuantityRequest.builder()
                .quantity(-1)
                .build();

        // When & Then
        mockMvc.perform(put("/carts/items/{cart_item_id}", TEST_CART_ITEM_ID)
                .header("X-USER-ID", TEST_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장바구니 아이템 수량 수정 - 유효하지 않은 cart_item_id")
    void testUpdateCartItemQuantity_Failed_InvalidCartItemId() throws Exception {
        // Given
        UpdateQuantityRequest request = UpdateQuantityRequest.builder()
                .quantity(5)
                .build();

        // When & Then
        mockMvc.perform(put("/carts/items/{cart_item_id}", "invalid")
                .header("X-USER-ID", TEST_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ========== 장바구니 아이템 제거 (DELETE /carts/items/{cart_item_id}) ==========

    @Test
    @DisplayName("장바구니 아이템 제거 - 성공")
    void testRemoveCartItem_Success() throws Exception {
        // Given
        doNothing().when(cartService).removeItem(TEST_USER_ID, TEST_CART_ITEM_ID);

        // When & Then
        mockMvc.perform(delete("/carts/items/{cart_item_id}", TEST_CART_ITEM_ID)
                .header("X-USER-ID", TEST_USER_ID))
                .andExpect(status().isNoContent());

        verify(cartService, times(1)).removeItem(TEST_USER_ID, TEST_CART_ITEM_ID);
    }

    @Test
    @DisplayName("장바구니 아이템 제거 - 음수 cart_item_id")
    void testRemoveCartItem_Failed_NegativeCartItemId() throws Exception {
        // When & Then
        mockMvc.perform(delete("/carts/items/{cart_item_id}", "-1")
                .header("X-USER-ID", TEST_USER_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장바구니 아이템 제거 - 유효하지 않은 cart_item_id")
    void testRemoveCartItem_Failed_InvalidCartItemId() throws Exception {
        // When & Then
        mockMvc.perform(delete("/carts/items/{cart_item_id}", "invalid")
                .header("X-USER-ID", TEST_USER_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장바구니 아이템 제거 - 헤더 누락")
    void testRemoveCartItem_Failed_MissingHeader() throws Exception {
        // When & Then
        mockMvc.perform(delete("/carts/items/{cart_item_id}", TEST_CART_ITEM_ID))
                .andExpect(status().isBadRequest());
    }

    // ========== 응답 포맷 검증 ==========

    @Test
    @DisplayName("장바구니 조회 - 응답 필드 검증")
    void testGetCart_ResponseFieldValidation() throws Exception {
        // Given
        CartResponseDto cartResponse = CartResponseDto.builder()
                .cartId(TEST_CART_ID)
                .userId(TEST_USER_ID)
                .totalItems(2)
                .totalPrice(100000L)
                .items(new ArrayList<>())
                .updatedAt(LocalDateTime.now())
                .build();

        when(cartService.getCartByUserId(TEST_USER_ID)).thenReturn(cartResponse);

        // When & Then
        mockMvc.perform(get("/carts")
                .header("X-USER-ID", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap())
                .andExpect(jsonPath("$.cart_id").isNumber())
                .andExpect(jsonPath("$.user_id").isNumber())
                .andExpect(jsonPath("$.total_items").isNumber())
                .andExpect(jsonPath("$.total_price").isNumber())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 응답 상태 코드 201")
    void testAddCartItem_ResponseStatusCode201() throws Exception {
        // Given
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(1)
                .build();

        CartItemResponse itemResponse = CartItemResponse.builder()
                .cartItemId(TEST_CART_ITEM_ID)
                .productId(TEST_PRODUCT_ID)
                .optionId(TEST_OPTION_ID)
                .quantity(1)
                .unitPrice(50000L)
                .subtotal(50000L)
                .build();

        when(cartService.addItem(eq(TEST_USER_ID), any(AddCartItemRequest.class)))
                .thenReturn(itemResponse);

        // When & Then
        mockMvc.perform(post("/carts/items")
                .header("X-USER-ID", TEST_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("장바구니 아이템 제거 - 응답 상태 코드 204")
    void testRemoveCartItem_ResponseStatusCode204() throws Exception {
        // Given
        doNothing().when(cartService).removeItem(TEST_USER_ID, TEST_CART_ITEM_ID);

        // When & Then
        mockMvc.perform(delete("/carts/items/{cart_item_id}", TEST_CART_ITEM_ID)
                .header("X-USER-ID", TEST_USER_ID))
                .andExpect(status().isNoContent());
    }
}
