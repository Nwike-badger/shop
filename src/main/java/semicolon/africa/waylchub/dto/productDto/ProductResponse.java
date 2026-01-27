package semicolon.africa.waylchub.dto.productDto;

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
    private List<ProductImage> images = new ArrayList<>();


}
