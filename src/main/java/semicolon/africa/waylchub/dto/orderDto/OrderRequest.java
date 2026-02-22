package semicolon.africa.waylchub.dto.orderDto;

import lombok.Data;
import semicolon.africa.waylchub.model.order.Address;

import java.util.List;

@Data
public class OrderRequest {
    private String customerId; // Or extract this from the JWT token in the controller
    private String customerEmail;

    private List<OrderItemRequest> items;

    private String paymentMethod; // e.g., "PAYSTACK"
    private Address shippingAddress;
    private Address billingAddress;

    private String orderNotes;
    private String appliedPromoCode; // Optional
}