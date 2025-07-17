package semicolon.africa.waylchub.dto.productDto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateCartItemRequest {
    @NotBlank(message = "User ID cannot be empty")
    private String userId;
    @NotBlank(message = "Product SKU cannot be empty")
    private String sku;
    @Min(value = 0, message = "Quantity cannot be negative")
    private int quantity;
}
