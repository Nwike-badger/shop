package semicolon.africa.waylchub.model.campaign;

import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "campaigns")
public class Campaign {

    @Id
    private String id;

    private String name;
    private String description;

    // ── TARGETING ──────────────────────────────────────────────────────────────
    // Product-level targeting (all three act as AND intersection)
    private String targetCategorySlug;
    private String targetBrandSlug;
    private String targetTag;
    private Set<String> targetProductIds;

    // Variant-level targeting — when set, ONLY these specific variants are
    // discounted. The parent product's basePrice is NOT touched.
    private Set<String> targetVariantIds;

    // ── DISCOUNT ───────────────────────────────────────────────────────────────
    private BigDecimal discountPercentage; // e.g. 20.0 = 20% off

    // ── SCHEDULE ───────────────────────────────────────────────────────────────
    private Instant startDate;
    private Instant endDate;

    // ── STATE ──────────────────────────────────────────────────────────────────
    @Builder.Default
    private boolean active = false;

    /**
     * CRITICAL FIX: Optimistic locking on Campaign itself.
     *
     * Prevents the race condition between the @Scheduled processCampaigns()
     * and a manual admin call to manualActivate(id) firing at the same
     * millisecond. The first thread to save active=true wins; the second
     * throws OptimisticLockingFailureException, which is caught and swallowed
     * (the job is already done by the winner).
     */
    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // ── HELPERS ────────────────────────────────────────────────────────────────

    /**
     * Returns true if this campaign targets individual variants rather than
     * whole products/categories. These two modes are mutually exclusive.
     */
    public boolean isVariantLevel() {
        return targetVariantIds != null && !targetVariantIds.isEmpty();
    }
}