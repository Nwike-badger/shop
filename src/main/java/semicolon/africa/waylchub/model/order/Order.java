package semicolon.africa.waylchub.model.order;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "orders")
public class Order {

    @Id
    private String id;

    // Human-readable order number (e.g., ORD-202602-ABCD)
    @Indexed(unique = true)
    private String orderNumber;

    @Indexed
    private String customerId; // Link to your User model

    private String customerEmail;

    // Embedded items
    private List<OrderItem> items;

    // Financial Totals
    private String currency;
    @Builder.Default
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();
    private BigDecimal itemSubTotal;
    private BigDecimal shippingFee;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal grandTotal;

    // Status Tracking
    private OrderStatus orderStatus;
    private PaymentStatus paymentStatus;

    // Payment Info
    private String paymentMethod; // e.g., "PAYSTACK", "FLUTTERWAVE", "STRIPE"
    private String paymentReference; // Transaction ID from the payment gateway

    // Shipping & Billing
    private Address shippingAddress;
    private Address billingAddress;

    private String orderNotes; // Optional message from customer

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private String courierName; // e.g., "GIG Logistics", "DHL"
    private String trackingNumber;
    private String trackingUrl;
    private String appliedPromoCode;

    @Version
    private Long version;
}