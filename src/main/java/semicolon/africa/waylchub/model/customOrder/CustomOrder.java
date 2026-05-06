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
import java.util.ArrayList;
import java.util.List;

/**
 * A made-to-measure order — quote-based, no upfront payment, no inventory
 * reduction (every order is unique production).
 *
 * Lives in a separate collection from {@code orders} so it doesn't pollute
 * the catalog flow with nullable fields. The Monnify payment service can
 * still be used for deposit/balance — see {@link PricingSpec}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "custom_orders")
@CompoundIndexes({
        // Admin dashboard: filter by status, newest first
        @CompoundIndex(name = "status_createdAt", def = "{'status': 1, 'createdAt': -1}"),
        // "My orders" page: per-customer chronological list
        @CompoundIndex(name = "customer_createdAt", def = "{'customerId': 1, 'createdAt': -1}")
})
public class CustomOrder {

    @Id
    private String id;

    /**
     * Human-readable reference. Format: EAB-CD-YYYYMMDD-XXXX
     * Quoted to clients on WhatsApp; clients reference it when replying.
     */
    @Indexed(unique = true)
    private String referenceNumber;

    // ─── Garment ──────────────────────────────────────────────────
    /** Slug like "agbada", "senator", "suit" — matches frontend CATEGORIES[].id */
    @Indexed
    private String categoryId;
    private String categoryName;

    /** Always MEN or WOMEN at submission time. */
    private Gender gender;

    // ─── Customer ─────────────────────────────────────────────────
    /** Null for guests. Indexed for authenticated "my orders" view. */
    @Indexed
    private String customerId;

    private String customerName;
    @Indexed
    private String customerEmail;
    private String whatsappNumber;
    private String phoneNumber;

    // ─── Specs (embedded) ─────────────────────────────────────────
    private StyleSpec style;
    private SizeSpec size;
    private DetailsSpec details;
    private DeliverySpec delivery;

    // ─── Pricing & status ─────────────────────────────────────────
    @Builder.Default
    private PricingSpec pricing = PricingSpec.builder().build();

    @Indexed
    @Builder.Default
    private CustomOrderStatus status = CustomOrderStatus.SUBMITTED;

    @Builder.Default
    private List<CustomOrderStatusHistory> statusHistory = new ArrayList<>();

    /**
     * Optional dedupe key sent by frontend on submit — prevents duplicate
     * orders from accidental double-clicks. If present, repeat submissions
     * with the same key return the original order rather than creating a new one.
     */
    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;

    // ─── Audit ────────────────────────────────────────────────────
    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}