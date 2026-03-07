package semicolon.africa.waylchub.dto.campaignDto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

@Data
@Builder
public class CampaignResponse {
    private String id;
    private String name;
    private String description;
    private String targetCategorySlug;
    private String targetBrandSlug;
    private String targetTag;
    private Set<String> targetProductIds;
    private Set<String> targetVariantIds;
    private BigDecimal discountPercentage;
    private Instant startDate;
    private Instant endDate;
    private boolean active;
    private int affectedProductCount;
    private int affectedVariantCount;
    private Instant createdAt;
    private Instant updatedAt;
}