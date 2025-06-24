package semicolon.africa.waylchub.dto.productDto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantRequest {
    @NotBlank(message = "SKU cannot be empty") // Added message
    private String sku;
    private Map<String, String> attributes;
    @NotNull(message = "Price cannot be null") // Added message
    @Min(value = 0, message = "Price must be non-negative") // Added message
    private BigDecimal price;
    @Min(value = 0, message = "Quantity must be non-negative") // Added message
    private int quantity;
    private String imageUrl; // Added: For variant-specific images
}