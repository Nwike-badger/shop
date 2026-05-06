package semicolon.africa.waylchub.model.customOrder;

/**
 * Constrained at submission time to MEN or WOMEN.
 * (Frontend forces a pick when category gender is unisex.)
 */
public enum Gender {
    MEN, WOMEN
}