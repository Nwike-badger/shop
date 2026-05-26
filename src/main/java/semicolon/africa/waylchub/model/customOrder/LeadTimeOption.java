package semicolon.africa.waylchub.model.customOrder;

/**
 * Production speed picked during the custom design wizard.
 * Used by the live price estimate:
 *   STANDARD: uses category's listed leadTime, no surcharge
 *   RUSH:     5-7 days, +25%
 */
public enum LeadTimeOption {
    STANDARD,
    RUSH
}