package semicolon.africa.waylchub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.model.event.FailedAggregateSync;
import semicolon.africa.waylchub.repository.event.FailedAggregateSyncRepository;
import semicolon.africa.waylchub.service.productService.ProductService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AggregateSyncScheduler {

    private final FailedAggregateSyncRepository failedSyncRepository;
    private final ProductService productService;

    /**
     * Runs every 5 minutes (300,000 milliseconds).
     * fixedDelay ensures the next run doesn't start until 5 minutes AFTER
     * the current run finishes, preventing overlapping jobs.
     */
    @Scheduled(fixedDelay = 300000)
    public void retryFailedAggregateSyncs() {
        List<FailedAggregateSync> failedSyncs = failedSyncRepository.findByResolvedFalse();

        if (failedSyncs.isEmpty()) {
            return; // Nothing to process
        }

        log.info("Found {} unresolved aggregate sync(s). Attempting recovery...", failedSyncs.size());

        // OPTIMIZATION: Group by Product ID.
        // If 10 updates failed for the same product, we only need to recalculate it once.
        Map<String, List<FailedAggregateSync>> groupedByProduct = failedSyncs.stream()
                .collect(Collectors.groupingBy(FailedAggregateSync::getProductId));

        for (Map.Entry<String, List<FailedAggregateSync>> entry : groupedByProduct.entrySet()) {
            String productId = entry.getKey();
            List<FailedAggregateSync> syncsForProduct = entry.getValue();

            try {
                // 1. Recalculate the parent aggregates
                productService.updateParentAggregates(productId);

                // 2. If successful, mark all records for this product as resolved
                for (FailedAggregateSync sync : syncsForProduct) {
                    sync.setResolved(true);
                    sync.setUpdatedAt(LocalDateTime.now());
                }
                failedSyncRepository.saveAll(syncsForProduct);

                log.info("Successfully recovered aggregate sync for product: {}", productId);

            } catch (Exception e) {
                // If it fails AGAIN, increment the attempt count and save the new error
                log.error("Recovery failed again for product {}: {}", productId, e.getMessage());

                for (FailedAggregateSync sync : syncsForProduct) {
                    sync.setAttemptCount(sync.getAttemptCount() + 1);
                    sync.setErrorMessage(e.getMessage());
                    sync.setUpdatedAt(LocalDateTime.now());
                }
                failedSyncRepository.saveAll(syncsForProduct);
            }
        }
    }
}