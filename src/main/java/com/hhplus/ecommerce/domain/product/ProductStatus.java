package com.hhplus.ecommerce.domain.product;

/**
 * 상품 판매 상태 열거형
 */
public enum ProductStatus {
    판매중("판매 중"),
    품절("품절"),
    판매중지("판매 중지");

    private final String displayName;

    ProductStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 문자열로부터 ProductStatus 조회
     * @param displayName 화면 표시명 (예: "판매 중")
     * @return ProductStatus 또는 기본값 판매중
     */
    public static ProductStatus from(String displayName) {
        for (ProductStatus status : ProductStatus.values()) {
            if (status.displayName.equals(displayName)) {
                return status;
            }
        }
        return 판매중; // 기본값
    }
}
