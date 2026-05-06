package semicolon.africa.waylchub.model.customOrder;

/** How the client provided their size. */
public enum SizeMode {
    /** Picked a standard size from the chart (S/M/L/XL...). */
    CHART,
    /** Entered exact measurements. */
    MANUAL,
    /** Requested a tailor visit (free in Aba, video call elsewhere). */
    TAILOR_VISIT
}