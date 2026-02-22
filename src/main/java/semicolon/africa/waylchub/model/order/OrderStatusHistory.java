package semicolon.africa.waylchub.model.order;

import lombok.*;

import java.time.LocalDateTime;

// New class
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusHistory {
    private OrderStatus status;
    private String note; // e.g., "Updated by Admin: John", "Webhook: Paystack successful"
    private LocalDateTime timestamp;
}
