package semicolon.africa.waylchub.dto.orderDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemRequest {
    private String variantId;
    private int quantity;
    private Map<String, String> variantAttributes;
}