package semicolon.africa.waylchub.dto.orderDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemRequest {
    private String variantId;
    private int quantity;
    private Map<String, String> variantAttributes;
}