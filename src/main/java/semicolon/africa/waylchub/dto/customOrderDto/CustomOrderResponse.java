package semicolon.africa.waylchub.dto.customOrderDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import semicolon.africa.waylchub.model.customOrder.*;

import java.time.Instant;
import java.util.List;

/**
 * What we return to the client after a submit, lookup, or admin list call.
 *
 * Mirrors {@link CustomOrder} but flattened for easier frontend consumption,
 * and never exposes internal Mongo {@code id} (we use referenceNumber instead).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomOrderResponse {

    private String referenceNumber;
    private String categoryId;
    private String categoryName;
    private Gender gender;

    // Customer
    private String customerName;
    private String customerEmail;
    private String whatsappNumber;
    private String phoneNumber;

    // Specs (full embed)
    private StyleSpec style;
    private SizeSpec size;
    private DetailsSpec details;
    private DeliverySpec delivery;
    private PricingSpec pricing;

    // Status
    private CustomOrderStatus status;
    private List<CustomOrderStatusHistory> statusHistory;

    // Audit
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Maps a CustomOrder entity to a response DTO.
     * Single place to maintain so adding/renaming fields doesn't drift.
     */
    public static CustomOrderResponse from(CustomOrder o) {
        return CustomOrderResponse.builder()
                .referenceNumber(o.getReferenceNumber())
                .categoryId(o.getCategoryId())
                .categoryName(o.getCategoryName())
                .gender(o.getGender())
                .customerName(o.getCustomerName())
                .customerEmail(o.getCustomerEmail())
                .whatsappNumber(o.getWhatsappNumber())
                .phoneNumber(o.getPhoneNumber())
                .style(o.getStyle())
                .size(o.getSize())
                .details(o.getDetails())
                .delivery(o.getDelivery())
                .pricing(o.getPricing())
                .status(o.getStatus())
                .statusHistory(o.getStatusHistory())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .build();
    }
}