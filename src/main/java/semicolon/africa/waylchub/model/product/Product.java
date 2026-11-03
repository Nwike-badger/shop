package semicolon.africa.waylchub.model.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.*;
import org.springframework.data.mongodb.core.mapping.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
@CompoundIndexes({
        @CompoundIndex(name = "category_price", def = "{'categorySlug': 1, 'minPrice': 1}"),
        // Category pages: matches the category itself AND everything under it
        @CompoundIndex(name = "lineage_ids_price", def = "{'categoryLineageIds': 1, 'minPrice': 1}"),
        @CompoundIndex(name = "brand_active", def = "{'brandName': 1, 'isActive': 1}")
})
public class Product {

    @Id
    private String id;

    @Indexed(unique = true)
    @NotBlank(message = "Slug is required")
    private String slug;

    @Indexed private String name;
    private String description;

    // =====================================================================
    // CATEGORIES — a product can be in MANY categories, related or not.
    //
    //   Unisex jogger: categoryIds = [menTrousersId, womenTrousersId, saleId]
    //
    // The FIRST entry is the PRIMARY category (breadcrumb, product card label).
    // categorySlug / categoryName / category always mirror that primary one,
    // so anything already reading those fields keeps working.
    //
    // All of these are kept in sync by ProductCategoryIndexer — never set
    // them by hand.
    // =====================================================================

    @Indexed
    @Builder.Default
    private List<String> categoryIds = new ArrayList<>();     // primary first

    @Indexed
    @Builder.Default
    private List<String> categorySlugs = new ArrayList<>();   // same order as categoryIds

    private String categorySlug;   // primary
    private String categoryName;   // primary

    /** Every assigned category + all their ancestors. Drives category pages. */
    @Builder.Default
    private List<String> categoryLineageIds = new ArrayList<>();

    @DBRef
    private Category category;     // primary

    // --- BRAND ---
    private String brandSlug;
    private String brandName;

    @DBRef
    private Brand brand;

    private String activeCampaignId;

    private BigDecimal originalBasePrice;

    @Indexed
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    @Min(value = 0, message = "Base price cannot be negative")
    private BigDecimal basePrice; // Represents the "From" price
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal compareAtPrice;
    private Integer totalStock;
    @Builder.Default
    @Min(value = 0, message = "Discount cannot be negative")
    private Integer discount = 0;

    // --- CONFIGURATION ---
    @Builder.Default
    private boolean isActive = true;

    @Builder.Default
    private Map<String, String> specifications = new HashMap<>();

    @Builder.Default
    private List<VariantOption> variantOptions = new ArrayList<>();

    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    // --- ANALYTICS ---
    @Builder.Default
    private Long soldCount = 0L;
    @Builder.Default
    private Double averageRating = 0.0;
    @Builder.Default
    private Integer reviewCount = 0;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}