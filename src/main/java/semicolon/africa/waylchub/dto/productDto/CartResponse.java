package semicolon.africa.waylchub.dto.productDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import semicolon.africa.waylchub.model.product.CartItem;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CartResponse {
    private String cartId;
    private String userId;
    private List<CartItem> items; // Can be CartItemResponse DTO if you want to control output further
    private BigDecimal totalPrice;
    private String message; // Optional: for success/error messages
}
