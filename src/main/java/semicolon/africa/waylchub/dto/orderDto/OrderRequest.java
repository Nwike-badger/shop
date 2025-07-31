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
    @NotNull
    @Valid // Ensure nested OrderItemRequest is also validated
    private List<OrderItemRequest> items;

    @NotNull
    @Valid // Ensure nested Address is also validated
    private Address shippingAddress;

    @NotNull
    private String paymentMethod; // e.g., "card", "cash_on_delivery"

    @Valid // Ensure nested PaymentDetailsRequest is also validated
    private PaymentDetailsRequest paymentDetails; // New: For card payments

    @NotNull
    private BigDecimal cartSubTotal;
    @NotNull
    private BigDecimal shippingFee;
    private BigDecimal discountAmount;
    @NotNull
    private BigDecimal totalAmount;
}
