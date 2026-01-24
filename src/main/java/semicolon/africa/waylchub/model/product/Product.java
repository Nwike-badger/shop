package semicolon.africa.waylchub.model.product;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "products")
// SCALABILITY FIX: Compound Indexes for common filter combinations
@CompoundIndexes({
        @CompoundIndex(name = "category_price", def = "{'category': 1, 'price': 1}"),
        @CompoundIndex(name = "name_text", def = "{'name': 'text', 'description': 'text'}") // Enable Text Search
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

    private BigDecimal price;
    private Integer stockQuantity;

    @DBRef
    private Category category;
    private Brand brand;

    // Optional: Denormalize Category Name to avoid DB Lookups on simple reads
    private String categoryName;
    private String categorySlug;

    // SCALABILITY FIX: Changed to Map for O(1) Access and easier filtering
    // Key = "Color", Value = "Red"
    private Map<String, String> attributes = new HashMap<>();

    private List<String> imageUrls = new ArrayList<>();

    private boolean isActive = true;
}