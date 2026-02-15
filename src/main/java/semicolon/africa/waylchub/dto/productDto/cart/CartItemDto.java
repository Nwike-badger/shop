package semicolon.africa.waylchub.dto.productDto.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDto {
    @NotBlank(message = "Product ID cannot be empty")
    private String productId; // Still needed to link back to the parent product
    @NotBlank(message = "SKU cannot be empty")
    private String sku; // This is the key identifier for the variant
    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity;
    // No need for name, price, imageUrls here as they are fetched from the variant.
}