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
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AddToCartRequest {
    @NotBlank(message = "User ID cannot be blank")
    private String userId;

    @NotEmpty(message = "Cart items cannot be empty")
    @Valid // This ensures validation is applied to each CartItemDto in the list
    private List<CartItemDto> items;
}

