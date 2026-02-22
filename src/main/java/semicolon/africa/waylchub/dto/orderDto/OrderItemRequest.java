package semicolon.africa.waylchub.dto.orderDto;

import lombok.Data;

@Data
public class OrderItemRequest {
    private String variantId;
    private int quantity;
}