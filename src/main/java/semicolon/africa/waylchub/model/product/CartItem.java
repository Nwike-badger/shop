package semicolon.africa.waylchub.model.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CartItem {
    private String productId;
    private String name;
    private BigDecimal price;
    private int quantity;
    private List<String> imageUrls;
}
