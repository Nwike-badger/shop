package semicolon.africa.waylchub.model.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductVariant {
    private String sku; // Unique identifier for variant
    private Map<String, String> attributes; // Dynamic attributes like Size, Color
    private BigDecimal price;
    private int quantity; // Remaining stock
    private String imageUrl; // Added: For variant-specific images
}