package semicolon.africa.waylchub.dto.productDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantResponseDto {
    private String sku;
    private Map<String, String> attributes;
    private BigDecimal price;
    private int quantity;
    private BigDecimal oldPrice;
    private List<String> imageUrls;
    private String discountPercentage;
    private String discountColorCode;

    public String getState() {
        return this.quantity > 0 ? "In Stock" : "Out of Stock";
    }
}