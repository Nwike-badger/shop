package semicolon.africa.waylchub.model.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.*;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * ✅ CRITICAL FIX #1: Added @Version for optimistic locking
 *
 * WHY THIS WAS CRITICAL:
 * Without @Version, concurrent updates to the same variant (e.g., one thread
 * reducing stock, another updating price) can overwrite each other silently.
 *
 * With @Version:
 * - MongoDB auto-increments version on each save
 * - Stale updates throw OptimisticLockingFailureException
 * - Your application can retry or handle gracefully
 *
 * Example scenario prevented:
 * Thread A: Load variant (v1), reduce stock 50→49
 * Thread B: Load variant (v1), update price
 * Thread A: Save (now v2) ✓
 * Thread B: Save fails with OptimisticLockingFailureException ✓
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "product_variants")
@CompoundIndexes({
        @CompoundIndex(name = "product_price_idx",
                def = "{'productId': 1, 'price': 1}"),
        @CompoundIndex(name = "product_stock_idx",
                def = "{'productId': 1, 'stockQuantity': 1}")
})
public class ProductVariant {

    @Id
    private String id;

    @Indexed
    private String productId;

    @Indexed(unique = true)
    @NotBlank(message = "SKU is required")
    private String sku;

    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price cannot be negative")
    private BigDecimal price;

    private BigDecimal compareAtPrice;

    @Min(value = 0, message = "Stock cannot be negative")
    private Integer stockQuantity;

    private Integer lowStockThreshold = 5;

    private boolean manageStock = true;

    @Builder.Default
    private Map<String, String> attributes = new HashMap<>();

    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    private boolean isActive = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /**
     * ✅ CRITICAL FIX: Added optimistic locking
     *
     * This field is automatically managed by Spring Data MongoDB:
     * - Auto-incremented on each save
     * - Checked on update (if version doesn't match, update fails)
     * - Prevents lost updates in concurrent scenarios
     */
    @Version
    private Long version;
}