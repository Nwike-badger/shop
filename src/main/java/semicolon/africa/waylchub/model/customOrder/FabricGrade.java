package semicolon.africa.waylchub.model.customOrder;

/**
 * Fabric quality tier picked during the custom design wizard.
 * Used by the live price estimate:
 *   STANDARD: +0% over base
 *   PREMIUM:  +30%
 *   LUXURY:   +60%
 */
public enum FabricGrade {
    STANDARD,
    PREMIUM,
    LUXURY
}