package semicolon.africa.waylchub.dto.productDto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import semicolon.africa.waylchub.model.product.ProductImage;

import java.math.BigDecimal; // Not directly used here
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map; // Not directly used here

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {
    private String id;
    private String name;
    private String slug;
    private BigDecimal price;
    private String categoryName; // Flattened for the frontend
    private String brandName;
    private int stockQuantity;
    private String categorySlug;
    private BigDecimal compareAtPrice;
    @Min(value = 0, message = "Discount cannot be negative")
    private Integer discount = 0;
    private Double averageRating = 0.0;
    private Integer reviewCount = 0;
    private List<ProductImage> images = new ArrayList<>();


}
