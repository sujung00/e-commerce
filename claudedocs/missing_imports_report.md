# Missing Imports Analysis Report

**Analysis Date**: 2025-11-18
**Total Test Files Analyzed**: 51
**Files Requiring Import Fixes**: 18
**Total Missing Imports**: 27

---

## Summary by Domain

### Cart Domain (5 files, 7 imports)
- `Cart` - 2 files
- `CartItem` - 2 files
- `CartItemNotFoundException` - 2 files
- `InvalidQuantityException` - 1 file

### Product Domain (5 files, 8 imports)
- `Product` - 2 files
- `ProductOption` - 4 files
- `ProductStatus` - 1 file
- `ProductNotFoundException` - 1 file

### Order Domain (3 files, 4 imports)
- `Order` - 1 file
- `OrderItem` - 2 files
- `OrderStatus` - 1 file
- `Outbox` - 1 file

### User Domain (3 files, 4 imports)
- `User` - 2 files
- `UserNotFoundException` - 1 file
- `InsufficientBalanceException` - 1 file

### Coupon Domain (2 files, 2 imports)
- `Coupon` - 1 file
- `CouponNotFoundException` - 1 file

---

## Detailed File-by-File Breakdown

### Application Layer Tests

#### CartServiceTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/application/cart/CartServiceTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.cart.Cart;
import com.hhplus.ecommerce.domain.cart.CartItem;
import com.hhplus.ecommerce.domain.cart.CartItemNotFoundException;
import com.hhplus.ecommerce.domain.cart.InvalidQuantityException;
```

---

### Domain Layer Tests - Cart

#### CartItemNotFoundExceptionTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/domain/cart/CartItemNotFoundExceptionTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.cart.CartItemNotFoundException;
```

#### CartItemTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/domain/cart/CartItemTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.cart.CartItem;
```

#### CartTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/domain/cart/CartTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.cart.Cart;
```

#### InvalidQuantityExceptionTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/domain/cart/InvalidQuantityExceptionTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.cart.InvalidQuantityException;
```

---

### Domain Layer Tests - Coupon

#### CouponNotFoundExceptionTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/domain/coupon/CouponNotFoundExceptionTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.coupon.CouponNotFoundException;
```

#### CouponTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/domain/coupon/CouponTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.coupon.Coupon;
```

---

### Domain Layer Tests - Order

#### OrderItemTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/domain/order/OrderItemTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.order.OrderItem;
```

#### OrderTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/domain/order/OrderTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.order.Order;
import com.hhplus.ecommerce.domain.order.OrderItem;
import com.hhplus.ecommerce.domain.order.OrderStatus;
```

#### OutboxTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/domain/order/OutboxTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.order.Outbox;
```

---

### Domain Layer Tests - Product

#### ProductDomainTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/domain/product/ProductDomainTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductNotFoundException;
import com.hhplus.ecommerce.domain.product.ProductOption;
```

#### ProductOptionDomainTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/domain/product/ProductOptionDomainTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.product.ProductOption;
```

#### ProductOptionTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/domain/product/ProductOptionTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.product.ProductOption;
```

#### ProductStatusTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/domain/product/ProductStatusTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.product.ProductStatus;
```

#### ProductTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/domain/product/ProductTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
```

---

### Domain Layer Tests - User

#### UserDomainTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/domain/user/UserDomainTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.user.InsufficientBalanceException;
import com.hhplus.ecommerce.domain.user.User;
```

#### UserNotFoundExceptionTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/domain/user/UserNotFoundExceptionTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.user.UserNotFoundException;
```

#### UserTest.java
**Path**: `/src/test/java/com/hhplus/ecommerce/unit/domain/user/UserTest.java`

**Missing Imports**:
```java
import com.hhplus.ecommerce.domain.user.User;
```

---

## Domain Package Verification

All domain classes follow this package structure:

```
com.hhplus.ecommerce.domain
├── cart
│   ├── Cart
│   ├── CartItem
│   ├── CartItemNotFoundException
│   └── InvalidQuantityException
├── coupon
│   ├── Coupon
│   ├── UserCoupon
│   └── CouponNotFoundException
├── order
│   ├── Order
│   ├── OrderItem
│   ├── OrderStatus
│   └── Outbox
├── product
│   ├── Product
│   ├── ProductOption
│   ├── ProductStatus
│   ├── ProductNotFoundException
│   └── InsufficientStockException
└── user
    ├── User
    ├── UserNotFoundException
    └── InsufficientBalanceException
```

---

## Next Steps

1. **Automated Fix**: Use the morphllm MCP tool or MultiEdit to add missing imports to all 18 files
2. **Verification**: Run `./gradlew compileTestJava` to verify compilation succeeds
3. **Test Execution**: Run `./gradlew test` to ensure all tests pass after import fixes

---

## Analysis Script

The analysis was performed using: `/scripts/analyze_missing_imports.py`

This script:
- Parsed 51 test files
- Extracted existing imports
- Identified class references in code
- Compared against known domain class mappings
- Generated accurate import statements for missing classes
