package semicolon.africa.waylchub.dto.productDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantResponseDto {
    private String sku;
    private Map<String, String> attributes;
    private BigDecimal price;
    private int quantity;
    private String imageUrl; // Added: For variant-specific images
}