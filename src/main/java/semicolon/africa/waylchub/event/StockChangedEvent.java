package semicolon.africa.waylchub.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * ✅ CRITICAL FIX #2: Event-driven parent aggregate updates
 *
 * WHY THIS IS NEEDED:
 * Under high load (100+ simultaneous checkouts), synchronous calls to
 * updateParentAggregates() create a bottleneck:
 *
 * OLD FLOW (BROKEN):
 * 1. User A buys variant → reduceStockAtomic() succeeds
 * 2. Immediately call updateParentAggregates(productId)
 * 3. 100 threads all try to update Product.totalStock simultaneously
 * 4. Only 1 succeeds per cycle due to @Version optimistic locking
 * 5. 99 threads throw OptimisticLockingFailureException
 * 6. User's order fails even though stock was successfully reduced!
 *
 * NEW FLOW (FIXED):
 * 1. User A buys variant → reduceStockAtomic() succeeds
 * 2. Publish StockChangedEvent (non-blocking)
 * 3. Order completes immediately ✓
 * 4. Async listener processes event in background
 * 5. Parent stats update eventually (1-2 seconds)
 * 6. If update fails due to concurrency, retry or skip (order already succeeded)
 */
@Getter
@RequiredArgsConstructor
public class StockChangedEvent {
    private final String productId;
    private final String variantId;
    private final int quantityChanged; // negative for reductions, positive for additions
}