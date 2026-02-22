package semicolon.africa.waylchub.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import semicolon.africa.waylchub.model.order.OrderItem;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class OrderCancelledEvent {
    private final String orderId;
    private final List<OrderItem> itemsToRestore;
}