package com.hhplus.ecommerce.presentation.cart.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장바구니 아이템 수량 업데이트 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuantityRequest {
    private Integer quantity;
}
