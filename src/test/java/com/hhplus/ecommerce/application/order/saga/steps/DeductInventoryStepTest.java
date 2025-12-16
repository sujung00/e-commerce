package com.hhplus.ecommerce.application.order.saga.steps;

import com.hhplus.ecommerce.application.order.dto.CreateOrderRequestDto.OrderItemDto;
import com.hhplus.ecommerce.application.order.saga.SagaContext;
import com.hhplus.ecommerce.domain.product.Product;
import com.hhplus.ecommerce.domain.product.ProductOption;
import com.hhplus.ecommerce.domain.product.ProductRepository;
import com.hhplus.ecommerce.integration.BaseIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeductInventoryStep 보상 로직 테스트
 *
 * 검증 포인트:
 * 1. SagaContext 플래그 기반 skip 로직
 * 2. 정상 보상 처리 (재고 복구)
 * 3. 보상 실패 시 Best Effort 동작
 * 4. DB 상태 변화 검증
 */
@SpringBootTest
@DisplayName("DeductInventoryStep 보상 로직 테스트")
class DeductInventoryStepTest extends BaseIntegrationTest {

    @Autowired
    private DeductInventoryStep deductInventoryStep;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate newTransactionTemplate;

    @Autowired
    public void setTransactionManager(PlatformTransactionManager tm) {
        this.newTransactionTemplate = new TransactionTemplate(tm);
        this.newTransactionTemplate.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    @Test
    @DisplayName("[DeductInventoryStep] Forward → Backward 정상 플로우 - 재고 차감 후 복구")
    void testCompensate_Success_RestoreStock() throws Exception {
        // Given - 테스트 데이터 준비
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long[] productIdArray = new long[1];
        long[] optionIdArray = new long[1];
        int initialStock = 100;
        int deductQuantity = 10;

        // 상품 및 옵션 생성
        newTransactionTemplate.execute(status -> {
            Product product = Product.builder()
                    .productName("재고테스트상품_" + testId)
                    .price(10000L)
                    .totalStock(initialStock)
                    .status("IN_STOCK")
                    .options(new ArrayList<>())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.save(product);
            entityManager.flush();

            ProductOption option = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("기본옵션")
                    .stock(initialStock)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option);
            entityManager.flush();

            productIdArray[0] = product.getProductId();
            optionIdArray[0] = option.getOptionId();
            return null;
        });

        long optionId = optionIdArray[0];

        // SagaContext 생성
        List<OrderItemDto> orderItems = List.of(
                new OrderItemDto(productIdArray[0], optionId, deductQuantity)
        );
        SagaContext context = new SagaContext(
                1L, // userId
                orderItems,
                null, // couponId
                0L, // couponDiscount
                100000L, // subtotal
                100000L  // finalAmount
        );

        // When - Forward Flow (재고 차감)
        deductInventoryStep.execute(context);

        // Then - Forward Flow 검증
        ProductOption afterDeduct = newTransactionTemplate.execute(status ->
            productRepository.findOptionById(optionId).orElseThrow()
        );
        assertEquals(initialStock - deductQuantity, afterDeduct.getStock(), "재고가 정상 차감되어야 함");
        assertTrue(context.isInventoryDeducted(), "inventoryDeducted 플래그가 true여야 함");
        assertEquals(deductQuantity, context.getDeductedInventory().get(optionId), "차감된 수량이 기록되어야 함");

        // When - Backward Flow (재고 복구)
        deductInventoryStep.compensate(context);

        // Then - Backward Flow 검증 (재고 복구)
        ProductOption afterCompensate = newTransactionTemplate.execute(status ->
            productRepository.findOptionById(optionId).orElseThrow()
        );
        assertEquals(initialStock, afterCompensate.getStock(), "재고가 복구되어야 함");
    }

    @Test
    @DisplayName("[DeductInventoryStep] 보상 skip - inventoryDeducted=false인 경우")
    void testCompensate_Skip_WhenInventoryNotDeducted() throws Exception {
        // Given - 테스트 데이터 준비
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long[] optionIdArray = new long[1];
        int initialStock = 100;

        // 상품 및 옵션 생성
        newTransactionTemplate.execute(status -> {
            Product product = Product.builder()
                    .productName("재고테스트상품_" + testId)
                    .price(10000L)
                    .totalStock(initialStock)
                    .status("IN_STOCK")
                    .options(new ArrayList<>())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.save(product);
            entityManager.flush();

            ProductOption option = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("기본옵션")
                    .stock(initialStock)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option);
            entityManager.flush();

            optionIdArray[0] = option.getOptionId();
            return null;
        });

        long optionId = optionIdArray[0];

        // SagaContext 생성 (inventoryDeducted=false)
        SagaContext context = new SagaContext(
                1L, // userId
                List.of(new OrderItemDto(1L, optionId, 10)),
                null, // couponId
                0L, // couponDiscount
                100000L, // subtotal
                100000L  // finalAmount
        );

        // When - Backward Flow (보상 시도)
        deductInventoryStep.compensate(context);

        // Then - 재고 변화 없음 (skip 되어야 함)
        ProductOption afterCompensate = newTransactionTemplate.execute(status ->
            productRepository.findOptionById(optionId).orElseThrow()
        );
        assertEquals(initialStock, afterCompensate.getStock(), "재고가 변하지 않아야 함");
    }

    @Test
    @DisplayName("[DeductInventoryStep] 다중 옵션 일괄 복구")
    void testCompensate_MultipleOptions_AllRestored() throws Exception {
        // Given - 테스트 데이터 준비
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long[] productIdArray = new long[1];
        long[] optionId1Array = new long[1];
        long[] optionId2Array = new long[1];
        int initialStock = 100;
        int deductQuantity1 = 10;
        int deductQuantity2 = 20;

        // 상품 및 2개 옵션 생성
        newTransactionTemplate.execute(status -> {
            Product product = Product.builder()
                    .productName("재고테스트상품_" + testId)
                    .price(10000L)
                    .totalStock(initialStock * 2)
                    .status("IN_STOCK")
                    .options(new ArrayList<>())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.save(product);
            entityManager.flush();

            ProductOption option1 = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("옵션1")
                    .stock(initialStock)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option1);

            ProductOption option2 = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("옵션2")
                    .stock(initialStock)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option2);
            entityManager.flush();

            productIdArray[0] = product.getProductId();
            optionId1Array[0] = option1.getOptionId();
            optionId2Array[0] = option2.getOptionId();
            return null;
        });

        long productId = productIdArray[0];
        long optionId1 = optionId1Array[0];
        long optionId2 = optionId2Array[0];

        // SagaContext 생성 (2개 옵션)
        List<OrderItemDto> orderItems = List.of(
                new OrderItemDto(productId, optionId1, deductQuantity1),
                new OrderItemDto(productId, optionId2, deductQuantity2)
        );
        SagaContext context = new SagaContext(
                1L, // userId
                orderItems,
                null, // couponId
                0L, // couponDiscount
                300000L, // subtotal
                300000L  // finalAmount
        );

        // When - Forward Flow (2개 옵션 재고 차감)
        deductInventoryStep.execute(context);

        // Then - Forward Flow 검증
        newTransactionTemplate.execute(status -> {
            ProductOption afterDeduct1 = productRepository.findOptionById(optionId1).orElseThrow();
            ProductOption afterDeduct2 = productRepository.findOptionById(optionId2).orElseThrow();

            assertEquals(initialStock - deductQuantity1, afterDeduct1.getStock(), "옵션1 재고 차감 확인");
            assertEquals(initialStock - deductQuantity2, afterDeduct2.getStock(), "옵션2 재고 차감 확인");
            return null;
        });

        // When - Backward Flow (2개 옵션 재고 복구)
        deductInventoryStep.compensate(context);

        // Then - Backward Flow 검증 (2개 옵션 모두 복구)
        newTransactionTemplate.execute(status -> {
            ProductOption afterCompensate1 = productRepository.findOptionById(optionId1).orElseThrow();
            ProductOption afterCompensate2 = productRepository.findOptionById(optionId2).orElseThrow();

            assertEquals(initialStock, afterCompensate1.getStock(), "옵션1 재고 복구 확인");
            assertEquals(initialStock, afterCompensate2.getStock(), "옵션2 재고 복구 확인");
            return null;
        });
    }

    @Test
    @DisplayName("[DeductInventoryStep] 보상 실패 시 Best Effort - 일부 옵션 복구 실패해도 계속 진행")
    void testCompensate_BestEffort_ContinueOnPartialFailure() throws Exception {
        // Given - 테스트 데이터 준비
        String testId = UUID.randomUUID().toString().substring(0, 8);
        long[] productIdArray = new long[1];
        long[] optionId1Array = new long[1];
        int initialStock = 100;
        int deductQuantity = 10;

        // 상품 및 옵션 생성
        newTransactionTemplate.execute(status -> {
            Product product = Product.builder()
                    .productName("재고테스트상품_" + testId)
                    .price(10000L)
                    .totalStock(initialStock)
                    .status("IN_STOCK")
                    .options(new ArrayList<>())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.save(product);
            entityManager.flush();

            ProductOption option1 = ProductOption.builder()
                    .productId(product.getProductId())
                    .name("옵션1")
                    .stock(initialStock)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            productRepository.saveOption(option1);
            entityManager.flush();

            productIdArray[0] = product.getProductId();
            optionId1Array[0] = option1.getOptionId();
            return null;
        });

        long productId = productIdArray[0];
        long optionId1 = optionId1Array[0];

        // SagaContext 생성 (정상 옵션 + 존재하지 않는 옵션)
        List<OrderItemDto> orderItems = List.of(
                new OrderItemDto(productId, optionId1, deductQuantity),
                new OrderItemDto(productId, 999999L, deductQuantity) // 존재하지 않는 옵션
        );
        SagaContext context = new SagaContext(
                1L, // userId
                orderItems,
                null, // couponId
                0L, // couponDiscount
                200000L, // subtotal
                200000L  // finalAmount
        );

        // Forward Flow에서 존재하지 않는 옵션으로 실패하므로,
        // 보상 시나리오를 만들기 위해 수동으로 context 설정
        context.setInventoryDeducted(true);
        context.recordInventoryDeduction(optionId1, deductQuantity);
        context.recordInventoryDeduction(999999L, deductQuantity); // 존재하지 않는 옵션

        // 재고 차감 (정상 옵션만)
        newTransactionTemplate.execute(status -> {
            ProductOption option1 = productRepository.findOptionById(optionId1).orElseThrow();
            option1.deductStock(deductQuantity);
            productRepository.saveOption(option1);
            return null;
        });

        // When - Backward Flow (보상 시도 - 일부 실패)
        // Best Effort: 존재하지 않는 옵션 복구 실패해도 예외 발생 안 함
        assertDoesNotThrow(() -> deductInventoryStep.compensate(context),
            "보상 중 일부 실패해도 예외가 발생하지 않아야 함 (Best Effort)");

        // Then - 정상 옵션은 복구되어야 함
        ProductOption afterCompensate1 = newTransactionTemplate.execute(status ->
            productRepository.findOptionById(optionId1).orElseThrow()
        );
        assertEquals(initialStock, afterCompensate1.getStock(), "정상 옵션은 복구되어야 함");
    }
}
