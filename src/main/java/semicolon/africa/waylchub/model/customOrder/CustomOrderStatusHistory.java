package semicolon.africa.waylchub.model.customOrder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One entry per status transition. Mirrors OrderStatusHistory pattern. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomOrderStatusHistory {
    private CustomOrderStatus status;
    private String note;
    private String actor;       // "system", "client", or admin user id
    private Instant timestamp;
}