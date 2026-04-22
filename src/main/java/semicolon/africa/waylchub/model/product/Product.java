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
        // For fast sorting/filtering on standard category pages
        @CompoundIndex(name = "category_price", def = "{'categorySlug': 1, 'minPrice': 1}"),

        // For fast sub-category tree expansion (Replaces the old regex lineage search)
        @CompoundIndex(name = "lineage_ids_price", def = "{'categoryLineageIds': 1, 'minPrice': 1}"),

        // For smart search brand-matching strategy
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



    private String categorySlug;



    private String activeCampaignId;


    private BigDecimal originalBasePrice;


    @Indexed
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    private List<String> categoryLineageIds = new ArrayList<>();

    private String categoryName;
    private String brandName;

    // --- RELATIONSHIPS ---
    @DBRef
    private Category category;

    @DBRef
    private Brand brand;

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
    private boolean isActive = true;

    @Builder.Default
    private Map<String, String> specifications = new HashMap<>();

    @Builder.Default
    private List<VariantOption> variantOptions = new ArrayList<>();

    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    // --- ANALYTICS ---
    private Long soldCount = 0L;
    private Double averageRating = 0.0;
    private Integer reviewCount = 0;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}