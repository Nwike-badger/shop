package semicolon.africa.waylchub.dto.orderDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import semicolon.africa.waylchub.model.product.Address;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderRequest {
    // userId will be extracted from authenticated user, not sent by frontend for security
    private String customerEmail;
    private List<OrderItemRequest> items;
}
