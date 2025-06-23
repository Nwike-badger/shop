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
    @NotBlank(message = "User ID cannot be blank")
    private String userId;

    @NotBlank(message = "Product ID cannot be blank")
    private String productId;

    @Min(value = 0, message = "Quantity must be non-negative") // 0 can mean remove
    private int quantity;
}
