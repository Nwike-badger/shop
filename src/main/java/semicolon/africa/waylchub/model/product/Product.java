package semicolon.africa.waylchub.model.product;

import lombok.Data;
import org.springframework.data.annotation.*; // Import for Auditing and Version
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "products")
@CompoundIndexes({
        @CompoundIndex(name = "category_price", def = "{'category': 1, 'price': 1}"),
        @CompoundIndex(name = "name_text", def = "{'name': 'text', 'description': 'text'}"),
        @CompoundIndex(name = "popularity", def = "{'soldCount': -1}") // NEW: For "Best Sellers"
})
public class Product {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    @Indexed(unique = true)
    private String slug;

    @Indexed(unique = true)
    private String sku;

    // --- PRICING & PROMOS ---
    // The current selling price (used for cart)
    private BigDecimal price;

    // The "Strikethrough" price (e.g., was 5000, now 4000).
    // If this is not null and > price, frontend shows "On Sale"
    private BigDecimal compareAtPrice;

    // Helps you quickly find all items on discount
    private boolean isOnSale = false;

    // --- INVENTORY ---
    private Integer stockQuantity;
    private Integer lowStockThreshold = 5; // Alert admin when stock hits this

    // --- ANALYTICS (For "Most Sold" & "Top Rated") ---
    // Updated by a background job or event whenever an order is completed
    private Long soldCount = 0L;

    // Updated whenever a new Review is added
    private Double averageRating = 0.0;
    private Integer reviewCount = 0;

    @DBRef
    private Category category;
    private Brand brand;

    // Denormalized fields
    private String categoryName;
    private String categorySlug;
    private String brandName;
    private Map<String, String> attributes = new HashMap<>();

    private List<ProductImage> images = new ArrayList<>();

    private boolean isActive = true;



    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version; // PREVENTS OVERWRITES.
    // If two admins edit this product at the same time, the second one fails safely.
}