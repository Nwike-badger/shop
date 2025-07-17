package semicolon.africa.waylchub.model.product;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map; // Not directly used in Product, but good to have if ProductVariant is here

@Document(collection = "products")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Product {
    @Id
    private String id;

    private String name;
    private String description;
    private String category;
    private String subCategory;
    private List<String> tags;
    private String brand;

    private String sku;

    private Map<String, String> attributes;
    private BigDecimal price;
    private BigDecimal oldPrice;
    private int quantity;
    private List<String> imageUrls;
    private String discountPercentage;
    private String discountColorCode;

    private int totalReviews;
    private double averageRating;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
