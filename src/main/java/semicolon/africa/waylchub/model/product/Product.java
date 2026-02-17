package semicolon.africa.waylchub.model.product;

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

        @CompoundIndex(name = "lineage_price", def = "{'categoryLineage': 1, 'minPrice': 1}"),
        @CompoundIndex(name = "text_search", def = "{'name': 'text', 'description': 'text', 'brandName': 'text'}")
})
public class Product {

    @Id
    private String id;

    @Indexed(unique = true)
    private String slug;

    @Indexed
    private String name;
    private String description;


    @Indexed
    private String categorySlug;

    @Indexed
    private String categoryLineage;

    private String categoryName;
    private String brandName;

    // --- RELATIONSHIPS ---
    @DBRef
    private Category category;

    @DBRef
    private Brand brand;

    // --- PRICING & STOCK ---
    private BigDecimal basePrice; // Represents the "From" price
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal compareAtPrice;
    private Integer totalStock;
    @Builder.Default
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