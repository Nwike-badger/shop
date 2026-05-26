package semicolon.africa.waylchub.model.customOrder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Optional details that help the tailor produce an accurate quote. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetailsSpec {

    @Builder.Default
    private FittingPreference fitting = FittingPreference.REGULAR;

    // ─── New fields: drive the live price estimate ────────────────────

    /**
     * Fabric quality tier picked in the wizard.
     * Standard / Premium / Luxury → 0% / +30% / +60% modifier.
     */
    @Builder.Default
    private FabricGrade fabricGrade = FabricGrade.STANDARD;

    /**
     * Embroidery intensity picked in the wizard.
     * None / Light / Heavy → 0% / +15% / +30% modifier.
     */
    @Builder.Default
    private EmbroideryLevel embroidery = EmbroideryLevel.NONE;

    /**
     * Production speed picked in the wizard.
     * Standard uses the category's leadTime. Rush adds +25%.
     */
    @Builder.Default
    private LeadTimeOption leadTime = LeadTimeOption.STANDARD;

    // ─── Existing free-form fields ─────────────────────────────────────

    /**
     * Free-form fabric description (e.g., "Cashmere", "Cotton", "Aso-Oke",
     * "Lace"). Independent of fabricGrade — customer can leave this blank
     * and just pick a grade, or specify a particular fabric.
     */
    private String fabric;

    /** Free-form (e.g., "Navy", "Cream", "Burgundy"). */
    private String color;

    /** Free-form (e.g., "Wedding", "Office", "Owambe"). */
    private String occasion;

    /** Optional deadline. Admin confirms feasibility in the quote. */
    private LocalDate needBy;

    /** Anything else the client wants the tailor to know. */
    private String notes;
}