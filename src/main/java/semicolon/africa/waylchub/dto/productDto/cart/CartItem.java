package semicolon.africa.waylchub.dto.productDto.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {
    @NotBlank(message = "Product ID cannot be empty")
    private String productId;
    private String variantId;
    @NotBlank(message = "SKU cannot be empty")
    private String sku; // This is the key identifier for the variant
    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity;
    private String productName;
    private String imageUrl;
    private BigDecimal unitPrice;
    private BigDecimal subTotal;
    private Map<String, String> variantAttributes;
    // No need for name, price, imageUrls here as they are fetched from the variant.
}