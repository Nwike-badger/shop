package semicolon.africa.waylchub.model.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import semicolon.africa.waylchub.model.product.Address;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "orders")
public class Order {
    @Id
    private String id;
    private String customerEmail;
    private String userId;
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    private String status; // PENDING, SUCCESS, CANCELLED
    private LocalDateTime createdAt = LocalDateTime.now();
}

//@Document(collection = "orders")
//@Data
//@AllArgsConstructor
//@NoArgsConstructor
//@Builder
//public class Order {
//    @Id
//    private String id;
//    private String userId; // ID of the user who placed the order
//
//    private List<OrderItem> items; // List of products ordered
//    private Address shippingAddress; // Shipping address for this specific order
//    private String paymentMethod; // e.g., "card", "cash_on_delivery"
//
//    // --- New fields for Payment Gateway Integration ---
//    private PaymentDetails paymentDetails; // Nested object to hold card details (for mock) or gateway response
//    private PaymentStatus paymentStatus; // e.g., PENDING, SUCCESS, FAILED, REFUNDED
//    // --- End New fields ---
//
//    private BigDecimal cartSubTotal;
//    private BigDecimal shippingFee;
//    private BigDecimal discountAmount;
//    private BigDecimal totalAmount;
//    private OrderStatus orderStatus; // e.g., PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELED
//
//    private LocalDateTime orderDate;
//    private LocalDateTime lastUpdated;
//}
