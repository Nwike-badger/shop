package semicolon.africa.waylchub.model.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderItem {
    private String productId;
    private String productName; // To save the name at the time of order
    private String imageUrl; // To save image URL at the time of order
    private int quantity;
    private BigDecimal unitPrice;// Price at the time the order was placed
    private BigDecimal subTotal;
}
