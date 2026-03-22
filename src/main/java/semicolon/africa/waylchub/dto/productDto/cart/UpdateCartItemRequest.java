package semicolon.africa.waylchub.dto.productDto.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Request body for PUT /api/v1/cart/update
 *
 * WHY NOT REUSE OrderItemRequest?
 * OrderItemRequest already exists and is used by both /cart/add and the order flow.
 * It likely carries variantAttributes which are irrelevant during an update
 * (attributes don't change — only quantity does). A dedicated DTO keeps validation
 * constraints clean and prevents accidental field leakage.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCartItemRequest {

    @NotBlank(message = "Variant ID must not be blank")
    private String variantId;

    /**
     * 0 is allowed — the controller treats quantity=0 as a remove instruction.
     * This lets the frontend send 0 instead of switching to a DELETE call
     * when the user clicks "-" on the last unit.
     */
    @Min(value = 0, message = "Quantity cannot be negative")
    private int quantity;
}
