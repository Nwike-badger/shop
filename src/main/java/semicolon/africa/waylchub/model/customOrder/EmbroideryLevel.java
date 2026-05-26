package semicolon.africa.waylchub.model.customOrder;

/**
 * Embroidery intensity picked during the custom design wizard.
 * Used by the live price estimate:
 *   NONE:  +0% over base
 *   LIGHT: +15%
 *   HEAVY: +30%
 */
public enum EmbroideryLevel {
    NONE,
    LIGHT,
    HEAVY
}