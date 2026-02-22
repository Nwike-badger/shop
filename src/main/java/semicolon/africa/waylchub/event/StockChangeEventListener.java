package semicolon.africa.waylchub.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import semicolon.africa.waylchub.model.event.FailedAggregateSync;
import semicolon.africa.waylchub.repository.event.FailedAggregateSyncRepository;
import semicolon.africa.waylchub.service.productService.ProductService;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockChangeEventListener {

    private final ProductService productService;
    private final FailedAggregateSyncRepository failedSyncRepository;

    /**
     * ✅ FIX: fallbackExecution = true
     * If called from a @Transactional method (like createOrder), it waits for the commit.
     * If called from a non-transactional method (like atomic updates), it fires immediately
     * instead of silently dropping the event.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Retryable(
            value = OptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void handleStockChange(StockChangedEvent event) {
        log.debug("Processing stock change event for product: {}", event.getProductId());
        productService.updateParentAggregates(event.getProductId());
        log.debug("Successfully updated aggregates for product: {}", event.getProductId());
    }

    @Recover
    public void recoverStockUpdate(OptimisticLockingFailureException e, StockChangedEvent event) {
        log.error("CRITICAL: Failed to update parent aggregates for product {} after all 3 retries.", event.getProductId());
        saveFailedSync(event, "OptimisticLockingFailureException", e.getMessage());
    }

    @Recover
    public void recoverGeneric(Exception e, StockChangedEvent event) {
        log.error("UNEXPECTED ERROR: Could not process stock event for product {}. Error: {}", event.getProductId(), e.getMessage());
        saveFailedSync(event, e.getClass().getSimpleName(), e.getMessage());
    }

    private void saveFailedSync(StockChangedEvent event, String reason, String errorMessage) {
        FailedAggregateSync failedSync = FailedAggregateSync.builder()
                .productId(event.getProductId())
                .variantId(event.getVariantId())
                .reason(reason)
                .errorMessage(errorMessage)
                .isResolved(false)
                .attemptCount(0)
                .build();

        failedSyncRepository.save(failedSync);
        log.info("Saved failed sync event to database for later processing. Product ID: {}", event.getProductId());
    }
}