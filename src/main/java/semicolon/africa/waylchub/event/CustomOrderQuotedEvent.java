package semicolon.africa.waylchub.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import semicolon.africa.waylchub.model.customOrder.CustomOrder;

/**
 * Fired after an admin successfully quotes a custom order.
 *
 * Primary listener is the WhatsApp notifier — it should send the client the
 * quote, deposit amount, and a payment link in one message.
 */
@Getter
@AllArgsConstructor
public class CustomOrderQuotedEvent {
    private final CustomOrder order;
}