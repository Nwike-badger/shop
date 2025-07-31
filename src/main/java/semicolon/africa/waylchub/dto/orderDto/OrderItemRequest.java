package semicolon.africa.waylchub.dto.orderDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderItemRequest {
    private String productId;
    private String productName;
    private String imageUrl;
    private int quantity;
    private BigDecimal priceAtPurchase;
}
