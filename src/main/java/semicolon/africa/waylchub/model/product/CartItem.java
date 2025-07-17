package semicolon.africa.waylchub.model.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CartItem {
    private String productId;
    private String sku;
    private String name;
    private Map<String, String> variantAttributes;
    private BigDecimal price;
    private int quantity;
    private List<String> imageUrls;
}
