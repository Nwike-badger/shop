package semicolon.africa.waylchub.model.customOrder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pricing for a custom order.
 *
 * At submit time the estimate fields are populated (the band the customer saw
 * in the wizard). The quoted fields are empty until admin reviews and quotes.
 *
 * After admin review:
 *   - quotedAmount is set
 *   - depositAmount is computed (50% of quote, rounded HALF_UP to 2dp)
 *   - balanceAmount is the rest
 *
 * Deposit and balance are tracked separately because Aba bespoke orders
 * standardly run on a 50% upfront / 50% on-delivery split. Both reference
 * Monnify payment IDs so the webhook can mark them paid.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingSpec {

    // ─── New: what the customer saw at submission time ────────────────

    /**
     * Lower bound of the live estimate the customer saw when submitting.
     * Both fields together represent the price band shown in the wizard footer.
     * Critical context for admin when issuing the final quote — if the quote
     * lands far outside this band you should justify it to the customer.
     */
    private BigDecimal estimatedPriceLow;

    /** Upper bound of the live estimate the customer saw. */
    private BigDecimal estimatedPriceHigh;

    // ─── Existing: admin-set quote ─────────────────────────────────────

    /** Total quoted price. Set when admin applies a quote. */
    private BigDecimal quotedAmount;

    /** Free-form notes from admin shown alongside the quote (fabric tier, etc). */
    private String quoteNotes;

    /** Admin user id who issued the quote. */
    private String quotedBy;

    /** When the quote was issued. */
    private Instant quotedAt;

    /** 50% of quotedAmount. Computed by service when quote is applied. */
    private BigDecimal depositAmount;

    /** True once the deposit Monnify payment is confirmed by webhook. */
    @Builder.Default
    private boolean depositPaid = false;

    /** Monnify payment reference for the deposit. */
    private String depositPaymentReference;

    /** quotedAmount - depositAmount. */
    private BigDecimal balanceAmount;

    /** True once the balance is settled (online or marked paid by admin). */
    @Builder.Default
    private boolean balancePaid = false;

    /** Monnify payment reference (or admin note) for the balance. */
    private String balancePaymentReference;
}