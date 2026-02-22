package semicolon.africa.waylchub.model.order;

import lombok.*;
import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    // Links back to the Product system
    private String productId;
    private String variantId;

    // Denormalized data (Snapshot of the product AT TIME OF PURCHASE)
    private String sku;
    private String productName;
    private String imageUrl;

    // E.g., {"Size": "XL", "Color": "Blue"}
    private Map<String, String> variantAttributes;

    private int quantity;

    // The price they actually paid per item
    private BigDecimal unitPrice;

    // quantity * unitPrice
    private BigDecimal subTotal;
}