package semicolon.africa.waylchub.dto.campaignDto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

@Data
public class CampaignRequest {

    private String id;

    @NotBlank(message = "Campaign name is required")
    private String name;
    private String description;

    // ── Product-level targeting (combined as AND) ──────────────────────────
    private String targetCategorySlug;
    private String targetBrandSlug;
    private String targetTag;
    private Set<String> targetProductIds;

    // ── Variant-level targeting (mutually exclusive with product targeting) ─
    private Set<String> targetVariantIds;

    @NotNull(message = "Discount percentage is required")
    @DecimalMin(value = "1.0")
    @DecimalMax(value = "99.0")
    private BigDecimal discountPercentage;

    @NotNull(message = "Start date is required")
    private Instant startDate;

    @NotNull(message = "End date is required")
    private Instant endDate;
}