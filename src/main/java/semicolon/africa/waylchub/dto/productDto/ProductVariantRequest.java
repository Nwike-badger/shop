package semicolon.africa.waylchub.dto.productDto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantRequest {
    @NotBlank(message = "SKU cannot be empty")
    private String sku;
    private Map<String, String> attributes;
    @NotNull(message = "Price cannot be null")
    @Min(value = 0, message = "Price must be non-negative")
    private BigDecimal price;
    @Min(value = 0, message = "Quantity must be non-negative")
    private int quantity;
    private List<String> imageUrls;
    private String discountPercentage;
    private String discountColorCode;
    private BigDecimal oldPrice;
}