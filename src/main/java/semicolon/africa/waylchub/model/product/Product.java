package semicolon.africa.waylchub.model.product;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map; // Not directly used in Product, but good to have if ProductVariant is here


@Data
@Document(collection = "products")
public class Product {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    @Indexed(unique = true)
    private String slug;

    @Indexed(unique = true)
    private String sku;

    private String description;

    private BigDecimal price;

    private Integer stockQuantity;

    // --- IMPORTANT CHANGE HERE ---
    @DBRef
    private Category category; // This must be 'category', NOT 'categoryId'
    // -----------------------------

    @DBRef
    private Brand brand;

    private List<ProductAttribute> attributes = new ArrayList<>();

    private boolean isActive = true;


}

//@Document(collection = "products")
//@Data
//@AllArgsConstructor
//@NoArgsConstructor
//@Builder
//public class Product {
//    @Id
//    private String id;
//
//    private String name;
//    private String description;
//    private String category;
//    private String subCategory;
//    private List<String> tags;
//    private String brand;
//
//    private String sku;
//
//    private Map<String, String> attributes;
//    private BigDecimal price;
//    private BigDecimal oldPrice;
//    private int quantity;
//    private List<String> imageUrls;
//    private String discountPercentage;
//    private String discountColorCode;
//
//    private int totalReviews;
//    private double averageRating;
//
//    private LocalDateTime createdAt;
//    private LocalDateTime updatedAt;
//}
