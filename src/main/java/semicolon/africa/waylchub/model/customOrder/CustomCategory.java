package semicolon.africa.waylchub.model.customOrder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One garment archetype that the customer can pick from the /custom landing
 * page (e.g. Agbada, Senator, Suit, Dress).
 *
 * Replaces the previous static {@code CustomCategoryRegistry} — this version
 * lives in MongoDB so admin can edit categories at runtime: change pricing,
 * upload a real cover image instead of an SVG silhouette, change the lead
 * time, deactivate seasonal items, etc.
 *
 * Backwards compatibility: orders submitted under the old static registry
 * still work because {@code categoryId} (slug like "agbada") is denormalized
 * onto every {@link CustomOrder} along with the human-readable name.
 *
 * The slug is the stable identifier. {@code id} is Mongo's internal id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "custom_categories")
public class CustomCategory {

    @Id
    private String id;

    /** Stable slug used in URLs and stored on orders. e.g. "agbada", "senator". */
    @Indexed(unique = true)
    private String slug;

    /** Human-readable name. e.g. "Agbada". */
    private String name;

    /** Short marketing line shown on the card. */
    private String tagline;

    /** Longer paragraph for the category page. */
    private String description;

    /**
     * Which gender the category targets. Values: "men", "women", "unisex".
     * Kept as String (not enum) for flexibility — admin may want to add
     * "kids" later without code changes. Validated in service layer.
     */
    private String genderHint;

    /** Starting price hint shown on the card; admin sends a real quote later. */
    private BigDecimal priceFrom;

    /** Free-form text — "5-7 days", "14-21 days". */
    private String leadTime;

    /** Hex colour used as accent for the card / hero (e.g. "#0d4d2a"). */
    private String accent;

    /**
     * Cover image URL — replaces the SVG silhouette. Either a Cloudinary URL
     * (admin uploaded) or any external URL the admin pasted.
     */
    private String coverImageUrl;

    /**
     * Optional Cloudinary public id, set when admin uploads via our endpoint.
     * Kept so we could clean up Cloudinary if image is replaced. Null for
     * pasted external URLs.
     */
    private String coverImagePublicId;

    /**
     * Which measurement set applies to this category. Mirrors the keys in the
     * frontend's {@code MEASUREMENT_SETS} (menFull, womenFull, unisexUpperLong,
     * etc). Kept as String so the frontend stays the source of truth on which
     * fields exist within a set — backend just records which one to use.
     */
    private String measurementSet;

    /**
     * SVG path for the silhouette icon — kept for backwards compatibility with
     * the existing card design. Will fall back to this if {@code coverImageUrl}
     * is empty. Eventually phasable out once all categories have real images.
     */
    private String silhouettePath;

    /** Display order on the customer-facing grid. Lower = earlier. */
    @Builder.Default
    private Integer sortOrder = 100;

    /** When false, hidden from /custom but still resolvable for existing orders. */
    @Builder.Default
    private Boolean active = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}