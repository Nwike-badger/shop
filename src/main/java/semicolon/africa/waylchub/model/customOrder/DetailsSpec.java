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

    /** Free-form (e.g., "Cashmere", "Cotton", "Aso-Oke", "Lace"). */
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