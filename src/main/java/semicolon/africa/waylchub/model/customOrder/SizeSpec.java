package semicolon.africa.waylchub.model.customOrder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Size portion of a custom order. Exactly one of three modes:
 *
 *   CHART        → chartSize is set (e.g., "L", "XL / 14")
 *   MANUAL       → measurements is populated (keys: "chest", "waist", "bust"...)
 *   TAILOR_VISIT → both above are empty; admin will book in-person measurement
 *
 * profileName is optional — set when the client saved this measurement set
 * for reuse on future orders (frontend localStorage feature).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SizeSpec {

    private SizeMode mode;

    /** Set when mode == CHART. e.g. "L", "XXL", "S / 8" */
    private String chartSize;

    /**
     * Set when mode == MANUAL. Keys match frontend MEASUREMENT_FIELDS ids
     * (chest, bust, waist, hip, sleeve, inseam...). Values are inches as strings
     * to preserve fractional input like "37.5".
     */
    @Builder.Default
    private Map<String, String> measurements = new HashMap<>();

    /** Optional name the client gave their measurement profile. */
    private String profileName;
}