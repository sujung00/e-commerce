package com.hhplus.ecommerce.domain.product;

import lombok.*;

import java.time.LocalDateTime;

/**
 * ProductOption 도메인 엔티티 (Rich Domain Model)
 *
 * 책임:
 * - 상품의 특정 옵션(색상, 사이즈 등)에 대한 재고 관리
 * - 재고 차감 및 복구 로직
 * - 재고 유효성 검증
 * - 낙관적 락을 통한 동시성 제어
 *
 * 핵심 비즈니스 규칙:
 * - 재고는 음수가 될 수 없음 (>= 0)
 * - 차감 시 충분한 재고 필요
 * - 복구 시 재고 합계 상한선 검증 필요
 * - version을 통한 동시성 제어 (낙관적 락)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductOption {
    private Long optionId;
    private Long productId;
    private String name;
    private Integer stock;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * ProductOption 생성 팩토리 메서드
     *
     * 비즈니스 규칙:
     * - 옵션명은 필수
     * - 초기 재고는 0 이상
     * - 초기 version은 1
     */
    public static ProductOption createOption(Long productId, String name, Integer initialStock) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("옵션명은 필수입니다");
        }
        if (initialStock == null || initialStock < 0) {
            throw new IllegalArgumentException("초기 재고는 0 이상이어야 합니다");
        }

        return ProductOption.builder()
                .productId(productId)
                .name(name)
                .stock(initialStock)
                .version(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 재고 차감 (주문 시)
     *
     * 비즈니스 규칙:
     * - 차감량은 0보다 커야 함
     * - 현재 재고 >= 차감량
     * - 성공 시 version 증가 (낙관적 락)
     * - updatedAt 갱신
     *
     * @param quantity 차감할 수량
     * @throws IllegalArgumentException 수량이 0 이하이거나 재고 부족
     */
    public void deductStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("차감 수량은 0보다 커야 합니다");
        }
        if (this.stock < quantity) {
            throw new IllegalArgumentException(
                    this.name + "의 재고가 부족합니다 (요청: " + quantity + ", 보유: " + this.stock + ")"
            );
        }

        this.stock -= quantity;
        this.version += 1;  // 낙관적 락: version 증가
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재고 복구 (주문 취소 시)
     *
     * 비즈니스 규칙:
     * - 복구량은 0보다 커야 함
     * - 성공 시 version 증가 (낙관적 락)
     * - updatedAt 갱신
     *
     * @param quantity 복구할 수량
     */
    public void restoreStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("복구 수량은 0보다 커야 합니다");
        }

        this.stock += quantity;
        this.version += 1;  // 낙관적 락: version 증가
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 특정 수량만큼 재고가 있는지 확인
     */
    public boolean hasStock(int quantity) {
        return this.stock >= quantity;
    }

    /**
     * 재고가 있는지 확인
     */
    public boolean hasAnyStock() {
        return this.stock > 0;
    }

    /**
     * 재고가 부족한지 확인
     */
    public boolean isOutOfStock() {
        return this.stock <= 0;
    }

    /**
     * 현재 재고량
     */
    public Integer getAvailableStock() {
        return this.stock;
    }

    /**
     * 버전 확인 (낙관적 락 검증)
     *
     * 다른 트랜잭션이 수정했는지 확인하기 위해 사용됩니다.
     */
    public boolean isVersionChanged(Long expectedVersion) {
        return !this.version.equals(expectedVersion);
    }

    /**
     * 옵션 정보 갱신
     */
    public void updateInfo(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
            this.updatedAt = LocalDateTime.now();
        }
    }
}
