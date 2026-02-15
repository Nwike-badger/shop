package semicolon.africa.waylchub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.model.product.ProductVariant;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductVariantRepository;
import semicolon.africa.waylchub.service.productService.ProductService;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ProductConcurrencyTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private ProductRepository productRepository; // 🚨 Added to set up and verify the parent Product

    @Test
    void testAtomicStockReductionUnderLoad() throws InterruptedException {

        // 1. Setup Parent Product
        Product parentProduct = new Product();
        parentProduct.setName("Concurrency Test Product");
        parentProduct.setTotalStock(10); // Set initial parent stock to match variant
        parentProduct = productRepository.save(parentProduct);
        String productId = parentProduct.getId();

        // 2. Setup a variant with 10 items
        ProductVariant variant = new ProductVariant();
        variant.setProductId(productId); // Link the variant to the parent product
        variant.setSku("TEST-SKU");
        variant.setStockQuantity(10);
        variant = variantRepository.save(variant);
        String variantId = variant.getId();

        // 3. Simulate 50 people trying to buy 1 item each simultaneously
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // Note: Ensure your implementation of reduceStockAtomic handles the parent aggregate!
                    productService.reduceStockAtomic(variantId, 1);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all "customers" to finish
        latch.await();

        // 4. Verify Variant Results
        ProductVariant updatedVariant = variantRepository.findById(variantId).get();
        System.out.println("Successes: " + successCount.get()); // Should be exactly 10
        System.out.println("Failures: " + failureCount.get());  // Should be exactly 40

        assertEquals(10, successCount.get(), "Only 10 purchases should succeed");
        assertEquals(40, failureCount.get(), "40 purchases should fail due to insufficient stock");
        assertEquals(0, updatedVariant.getStockQuantity(), "Variant stock should be exactly 0");

        // 🚨 5. NEW: Verify Parent Product Results
        Product updatedProduct = productRepository.findById(productId).get();
        System.out.println("Final Parent Stock: " + updatedProduct.getTotalStock()); // Should be 0
        assertEquals(0, updatedProduct.getTotalStock(), "Parent product total stock must also be 0");
    }
}

