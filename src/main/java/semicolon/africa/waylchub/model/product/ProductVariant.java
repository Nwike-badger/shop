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
        @CompoundIndex(name = "product_price_idx",  def = "{'productId': 1, 'price': 1}"),
        @CompoundIndex(name = "product_stock_idx",  def = "{'productId': 1, 'stockQuantity': 1}")
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

    // ── CAMPAIGN FIELDS (NEW) ─────────────────────────────────────────────────
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

    @Version
    private Long version;
}