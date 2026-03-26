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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "product_variants")
@CompoundIndexes({
        @CompoundIndex(name = "product_price_idx", def = "{'productId': 1, 'price': 1}"),
        @CompoundIndex(name = "product_stock_idx", def = "{'productId': 1, 'stockQuantity': 1}"),

        /**
         * FIX: This index makes duplicate variant prevention concurrency-safe.
         *
         * The in-memory check in ProductService.saveVariant() is a fast-path
         * optimisation for the common case. This index is the real enforcer —
         * if two requests race past the in-memory check simultaneously, MongoDB
         * rejects the second insert with DuplicateKeyException.
         *
         * Handle it in your global exception handler:
         *
         *   @ExceptionHandler(DuplicateKeyException.class)
         *   public ResponseEntity<ApiError> handleDuplicate(DuplicateKeyException ex) {
         *       return ResponseEntity.status(HttpStatus.CONFLICT)
         *           .body(new ApiError("A variant with these attributes already exists"));
         *   }
         *
         * Note: 'attributes' is a Map<String, String>. MongoDB stores maps as
         * embedded documents and supports compound indexes on them correctly.
         */
        @CompoundIndex(
                name   = "variant_uniqueness_idx",
                def    = "{'productId': 1, 'attributes': 1}",
                unique = true
        )
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

    private BigDecimal compareAtPrice;  // display-only: "was ₦X"

    // ── CAMPAIGN FIELDS ───────────────────────────────────────────────────────
    @Indexed
    private String activeCampaignId;    // which campaign owns this variant's price
    private BigDecimal originalPrice;   // safe backup used only by campaign engine
    // ─────────────────────────────────────────────────────────────────────────

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
     * Optimistic locking. Spring Data MongoDB increments this on every save and
     * rejects concurrent writes that have a stale version with
     * OptimisticLockingFailureException.
     *
     * This is why ProductService.updateParentAggregates() uses mongoTemplate
     * atomic $set operations rather than a read-modify-write save() cycle —
     * bypassing @Version for aggregate updates avoids version conflicts under
     * concurrent StockChangedEvent processing.
     */
    @Version
    private Long version;
}