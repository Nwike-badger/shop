package semicolon.africa.waylchub.dto.customOrderDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import semicolon.africa.waylchub.model.customOrder.*;
import semicolon.africa.waylchub.model.order.Address;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Submission payload — everything the frontend sends in one POST.
 *
 * Validation here is structural only (required fields exist, sizes within
 * bounds). Cross-field validation (style requires either selection or images,
 * size requires either chart/manual/visit, delivery NIGERIA requires address)
 * is enforced in CustomOrderService.submit so we can return clear messages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomOrderRequest {

    /** Slug like "agbada". */
    @NotBlank
    private String categoryId;

    /** MEN or WOMEN — required even for unisex categories (frontend forces a pick). */
    @NotNull
    private Gender gender;

    // ─── Style ────────────────────────────────────────────────────
    private String selectedStyleId;

    @Valid
    @Size(max = 4, message = "Maximum 4 reference images")
    private List<StyleImageInput> referenceImages;

    @Size(max = 1000)
    private String styleNotes;

    // ─── Size ─────────────────────────────────────────────────────
    @NotNull
    private SizeMode sizeMode;

    private String chartSize;

    @Builder.Default
    private Map<String, String> measurements = new HashMap<>();

    private String profileName;

    // ─── Details ──────────────────────────────────────────────────
    @Builder.Default
    private FittingPreference fitting = FittingPreference.REGULAR;

    private String fabric;
    private String color;
    private String occasion;
    private LocalDate needBy;

    @Size(max = 2000)
    private String notes;

    // ─── Contact ──────────────────────────────────────────────────
    @NotBlank
    private String customerName;

    /** Either whatsappNumber or phoneNumber must be present (validated in service). */
    private String whatsappNumber;
    private String phoneNumber;
    private String customerEmail;

    // ─── Delivery ─────────────────────────────────────────────────
    @NotNull
    @Builder.Default
    private DeliveryMode deliveryMode = DeliveryMode.ABA;

    private Address deliveryAddress;

    /** Optional dedupe key. If set, repeat submissions return the original order. */
    private String idempotencyKey;
}