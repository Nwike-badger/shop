package semicolon.africa.waylchub.model.customOrder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One gallery item inside a category — what the customer picks during the
 * Style step of the wizard ("Classic Royal", "Modern Slim", etc.).
 *
 * One {@link CustomCategory} → many {@code CustomStyle}s.
 *
 * The relationship is denormalized (categorySlug) rather than @DBRef:
 *  - Reads are 1 query for the catalog endpoint, not N+1
 *  - Renaming a category slug is rare and can be a one-time admin script
 *  - Matches the pattern your existing Product → Category uses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "custom_styles")
@CompoundIndexes({
        // Customer-facing query: "all active styles for category X, in order"
        @CompoundIndex(name = "category_active_sort",
                def = "{'categorySlug': 1, 'active': 1, 'sortOrder': 1}")
})
public class CustomStyle {

    @Id
    private String id;

    /** Stable identifier — kept on submitted orders so renames don't break history. */
    @Indexed(unique = true)
    private String slug;

    /** Slug of the parent category. e.g. "agbada", "senator". */
    @Indexed
    private String categorySlug;

    /** Human-readable name. e.g. "Classic Royal", "Modern Slim". */
    private String name;

    /** Short descriptor shown under the name. e.g. "Cream / Gold embroidery". */
    private String tone;

    /** Real photo URL — Cloudinary upload OR pasted external URL. */
    private String imageUrl;

    /** Cloudinary public_id for cleanup; null if external URL. */
    private String imagePublicId;

    /** Optional richer description for tooltip / info modal. */
    private String description;

    /** Display order within the category. Lower = earlier. */
    @Builder.Default
    private Integer sortOrder = 100;

    /** When false, hidden from customer gallery but kept for order history. */
    @Builder.Default
    private Boolean active = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}