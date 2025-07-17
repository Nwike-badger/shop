package semicolon.africa.waylchub.dto.productDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddToCartRequest {
    @NotBlank(message = "User ID cannot be empty")
    private String userId;
    @NotEmpty(message = "Items list cannot be empty")
    @Valid
    private List<CartItemDto> items;
}

