package semicolon.africa.waylchub.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import semicolon.africa.waylchub.model.customOrder.CustomOrder;

/**
 * Fired after a custom order is successfully persisted.
 *
 * Listeners can:
 *   - Send the admin team a WhatsApp/email notification
 *   - Send the client a confirmation email
 *   - Push to a real-time admin dashboard
 *
 * Use {@code @TransactionalEventListener(phase = AFTER_COMMIT)} on listeners
 * so notifications only fire after the order is committed to MongoDB.
 */
@Getter
@AllArgsConstructor
public class CustomOrderSubmittedEvent {
    private final CustomOrder order;
}