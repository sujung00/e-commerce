package com.hhplus.ecommerce.presentation.cart;

import com.hhplus.ecommerce.application.cart.CartService;
import com.hhplus.ecommerce.presentation.cart.request.AddCartItemRequest;
import com.hhplus.ecommerce.presentation.cart.request.UpdateQuantityRequest;
import com.hhplus.ecommerce.presentation.cart.response.CartItemResponse;
import com.hhplus.ecommerce.presentation.cart.response.CartResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * CartController - Presentation 계층
 * 장바구니 API 요청 처리
 */
@RestController
@RequestMapping("/carts")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * GET /carts - 장바구니 조회
     */
    @GetMapping
    public ResponseEntity<CartResponseDto> getCart(@RequestHeader("X-USER-ID") Long userId) {
        CartResponseDto cart = cartService.getCartByUserId(userId);
        return ResponseEntity.ok(cart);
    }

    /**
     * POST /carts/items - 장바구니 아이템 추가
     */
    @PostMapping("/items")
    public ResponseEntity<CartItemResponse> addCartItem(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestBody AddCartItemRequest request) {
        CartItemResponse response = cartService.addItem(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PUT /carts/items/{cart_item_id} - 장바구니 아이템 수량 수정
     */
    @PutMapping("/items/{cart_item_id}")
    public ResponseEntity<CartItemResponse> updateCartItemQuantity(
            @RequestHeader("X-USER-ID") Long userId,
            @PathVariable("cart_item_id") Long cartItemId,
            @RequestBody UpdateQuantityRequest request) {
        CartItemResponse response = cartService.updateItemQuantity(userId, cartItemId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /carts/items/{cart_item_id} - 장바구니 아이템 제거
     */
    @DeleteMapping("/items/{cart_item_id}")
    public ResponseEntity<Void> removeCartItem(
            @RequestHeader("X-USER-ID") Long userId,
            @PathVariable("cart_item_id") Long cartItemId) {
        cartService.removeItem(userId, cartItemId);
        return ResponseEntity.noContent().build();
    }
}
