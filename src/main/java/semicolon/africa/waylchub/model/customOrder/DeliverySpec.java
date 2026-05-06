package semicolon.africa.waylchub.model.customOrder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import semicolon.africa.waylchub.model.order.Address;

/**
 * Delivery preference for a custom order.
 *
 * For ABA mode, address is optional (client may pick up from shop).
 * For NIGERIA mode, address must be present — validated at submit time.
 *
 * Reuses {@link Address} from the existing order package so addresses
 * captured here can be promoted to a saved customer address later if needed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliverySpec {

    @Builder.Default
    private DeliveryMode mode = DeliveryMode.ABA;

    /** Optional for ABA mode, required for NIGERIA. */
    private Address address;
}