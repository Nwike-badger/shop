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

    private String name;

    private BigDecimal price;

    private int quantity;

    private String imageUrl;
}
