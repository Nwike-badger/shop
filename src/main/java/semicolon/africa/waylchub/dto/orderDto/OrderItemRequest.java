package semicolon.africa.waylchub.dto.orderDto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
    @NotNull
    private String productId; // Changed from Long to String for MongoDB

    @Min(1)
    private int quantity;
}
