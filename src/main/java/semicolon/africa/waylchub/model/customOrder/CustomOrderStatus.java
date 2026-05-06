package semicolon.africa.waylchub.model.customOrder;

/**
 * Lifecycle states for a custom (made-to-measure) order.
 *
 * Flow:
 *   SUBMITTED       → client submitted, awaiting admin review
 *   QUOTED          → admin sent quote via WhatsApp
 *   DEPOSIT_PAID    → client paid 50% deposit, production starts
 *   IN_PRODUCTION   → cutting/sewing in progress
 *   READY           → ready for fitting or delivery
 *   SHIPPED         → handed to courier (only for non-Aba)
 *   DELIVERED       → client received it; balance now due
 *   COMPLETED       → balance paid, order closed
 *   CANCELLED       → cancelled by client or admin
 *   REJECTED        → admin declined to take the order
 */
public enum CustomOrderStatus {
    SUBMITTED,
    QUOTED,
    DEPOSIT_PAID,
    IN_PRODUCTION,
    READY,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    REJECTED
}