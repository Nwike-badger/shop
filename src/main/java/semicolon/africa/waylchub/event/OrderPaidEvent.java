package semicolon.africa.waylchub.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import semicolon.africa.waylchub.model.order.Order;

@Getter
@AllArgsConstructor
public class OrderPaidEvent {
    private final Order order;
    private final String transactionReference;
}
