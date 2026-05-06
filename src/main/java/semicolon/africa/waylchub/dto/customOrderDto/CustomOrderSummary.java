package semicolon.africa.waylchub.dto.customOrderDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import semicolon.africa.waylchub.model.customOrder.CustomOrder;
import semicolon.africa.waylchub.model.customOrder.CustomOrderStatus;
import semicolon.africa.waylchub.model.customOrder.Gender;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lightweight projection for admin list / search results.
 * Avoids shipping all the embedded specs over the wire when only headers
 * are shown in a table view.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomOrderSummary {
    private String referenceNumber;
    private String categoryId;
    private String categoryName;
    private Gender gender;
    private String customerName;
    private String whatsappNumber;
    private CustomOrderStatus status;
    private BigDecimal quotedAmount;
    private boolean depositPaid;
    private Instant createdAt;

    public static CustomOrderSummary from(CustomOrder o) {
        return CustomOrderSummary.builder()
                .referenceNumber(o.getReferenceNumber())
                .categoryId(o.getCategoryId())
                .categoryName(o.getCategoryName())
                .gender(o.getGender())
                .customerName(o.getCustomerName())
                .whatsappNumber(o.getWhatsappNumber())
                .status(o.getStatus())
                .quotedAmount(o.getPricing() != null ? o.getPricing().getQuotedAmount() : null)
                .depositPaid(o.getPricing() != null && o.getPricing().isDepositPaid())
                .createdAt(o.getCreatedAt())
                .build();
    }
}