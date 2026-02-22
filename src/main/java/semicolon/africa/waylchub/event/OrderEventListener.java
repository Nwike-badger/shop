package semicolon.africa.waylchub.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import semicolon.africa.waylchub.model.order.OrderItem;
import semicolon.africa.waylchub.service.productService.ProductService;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final ProductService productService;

    /**
     * Runs asynchronously ONLY AFTER the cancellation transaction commits to the DB.
     * Restores stock seamlessly without blocking the user's API response.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCancellation(OrderCancelledEvent event) {
        log.info("Processing asynchronous stock restoration for cancelled order: {}", event.getOrderId());

        for (OrderItem item : event.getItemsToRestore()) {
            try {
                productService.addStockAtomic(item.getVariantId(), item.getProductId(), item.getQuantity());
            } catch (Exception e) {
                log.error("Failed to restore stock for variant {} from order {}", item.getVariantId(), event.getOrderId(), e);
                // In a massive enterprise app, you'd save this failure to a Dead Letter Queue (like we did for aggregates)
            }
        }
    }
}