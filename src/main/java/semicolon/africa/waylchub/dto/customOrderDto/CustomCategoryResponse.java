package semicolon.africa.waylchub.dto.customOrderDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import semicolon.africa.waylchub.model.customOrder.CustomCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Returned by both the customer-facing /custom-catalog and admin endpoints.
 *
 * @JsonInclude(NON_NULL): null fields are omitted from the JSON response.
 * This prevents Jackson throwing on unregistered type serializers for Instant
 * in environments where JavaTimeModule isn't on the classpath, and keeps
 * the payload lean for the customer landing page.
 *
 * The customer endpoint does NOT need createdAt/updatedAt — the admin editor
 * does, and they're included when non-null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomCategoryResponse {

    private String slug;
    private String name;
    private String tagline;
    private String description;
    private String genderHint;
    private BigDecimal priceFrom;
    private String leadTime;
    private String accent;
    private String coverImageUrl;
    private String coverImagePublicId;
    private String measurementSet;
    private String silhouettePath;
    private Integer sortOrder;
    private Boolean active;

    /** Populated by the single-category endpoint — null on list views. */
    private List<CustomStyleResponse> sampleStyles;

    /** Admin-only — count of orders attached to this category. */
    private Long orderCount;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Null-safe factory. Every getter is guarded so a partially-migrated
     * or seeder-created document with missing fields won't NPE here.
     */
    public static CustomCategoryResponse from(CustomCategory c) {
        if (c == null) return null;

        return CustomCategoryResponse.builder()
                .slug(c.getSlug())
                .name(c.getName())
                .tagline(c.getTagline())
                .description(c.getDescription())
                .genderHint(c.getGenderHint())
                .priceFrom(c.getPriceFrom())
                .leadTime(c.getLeadTime())
                .accent(c.getAccent())
                .coverImageUrl(c.getCoverImageUrl())
                .coverImagePublicId(c.getCoverImagePublicId())
                .measurementSet(c.getMeasurementSet())
                .silhouettePath(c.getSilhouettePath())
                .sortOrder(c.getSortOrder())
                .active(c.getActive())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}