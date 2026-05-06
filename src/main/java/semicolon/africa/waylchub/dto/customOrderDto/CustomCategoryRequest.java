package semicolon.africa.waylchub.dto.customOrderDto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Admin payload to create or update a {@link semicolon.africa.waylchub.model.customOrder.CustomCategory}.
 *
 * Slug is required only on create — a re-saved category keeps its slug to
 * preserve order history. The controller decides which path is being taken.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomCategoryRequest {

    /** URL-safe slug. Required on create, ignored on update. */
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must be lowercase letters, numbers, or hyphens only")
    @Size(min = 2, max = 60)
    private String slug;

    /**
     * Required on create, optional on update (service-layer enforces presence on create).
     * @NotBlank removed intentionally — this DTO is shared between POST (create) and
     * PUT (update). On update, partial payloads are valid. The service validates name
     * is present on create explicitly, and the frontend always sends it anyway.
     */
    @Size(min = 1, max = 80)
    private String name;

    @Size(max = 200)
    private String tagline;

    @Size(max = 2000)
    private String description;

    /** "men", "women", or "unisex". */
    @Pattern(regexp = "men|women|unisex", message = "Gender must be men, women, or unisex")
    private String genderHint;

    @DecimalMin(value = "0.0", inclusive = true, message = "Price cannot be negative")
    private BigDecimal priceFrom;

    @Size(max = 50)
    private String leadTime;

    /** Hex colour like #0d4d2a. */
    @Pattern(regexp = "^#?[0-9a-fA-F]{3,8}$", message = "Accent must be a hex colour")
    private String accent;

    /** Either Cloudinary URL or pasted external URL. */
    @Size(max = 1000)
    private String coverImageUrl;

    /** Optional Cloudinary public_id, set when uploaded via our endpoint. */
    @Size(max = 200)
    private String coverImagePublicId;

    /** Measurement set key — must match a key in frontend MEASUREMENT_SETS. */
    @Size(max = 50)
    private String measurementSet;

    /** Optional fallback silhouette path (used when no cover image yet). */
    private String silhouettePath;

    private Integer sortOrder;

    private Boolean active;
}