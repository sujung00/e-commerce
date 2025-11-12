package com.hhplus.ecommerce.application.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장바구니 수량 수정 커맨드 (Application layer 내부 DTO)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuantityCommand {
    private Integer quantity;
}