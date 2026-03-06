package semicolon.africa.waylchub.model.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data  // <--- This generates the getVariantId() method
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {
    private String productId;

    // ✅ Make sure this field exists!
    private String variantId;

    private String productName;
    private String sku;
    private String imageUrl;
    private int quantity;

    private BigDecimal unitPrice;
    private BigDecimal subTotal;
    private Map<String, String> variantAttributes;
}