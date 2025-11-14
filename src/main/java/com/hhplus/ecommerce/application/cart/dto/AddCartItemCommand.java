package com.hhplus.ecommerce.application.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장바구니 항목 추가 커맨드 (Application layer 내부 DTO)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddCartItemCommand {
    private Long productId;
    private Long optionId;
    private Integer quantity;
}