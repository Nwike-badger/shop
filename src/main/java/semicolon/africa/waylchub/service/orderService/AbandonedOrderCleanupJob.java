package semicolon.africa.waylchub.service.orderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.event.OrderCancelledEvent;
import semicolon.africa.waylchub.model.order.Order;
import semicolon.africa.waylchub.model.order.OrderStatus;
import semicolon.africa.waylchub.repository.orderRepository.OrderRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Automatically cancels orders that have been in PENDING_PAYMENT status
 * for more than 30 minutes, and fires OrderCancelledEvent to restore stock.
 *
 * WHY THIS MATTERS:
 * When a user starts checkout but abandons payment, the order is created and
 * stock is reduced. Without this job, that stock is locked forever — the product
 * shows as sold out even though no money changed hands.
 *
 * ShedLock ensures only ONE instance runs this in a multi-pod deployment.
 *
 * WHY ROUTE THROUGH OrderService.cancelOrder():
 *   The original job set orderStatus directly on the entity and saved via the
 *   repository. This bypassed two important concerns:
 *
 *   1. STATE MACHINE — validateStatusTransition() in OrderService ensures
 *      only valid transitions are allowed. Bypassing it means the cleanup job
 *      could cancel a SHIPPED or DELIVERED order if the status check window
 *      raced with an admin update.
 *
 *   2. AUDIT HISTORY — addStatusHistory() records every status change with
 *      a timestamp and reason. Without it, auto-cancelled orders show no
 *      history entry, making support investigation impossible.
 *
 *   Routing through the service gets both for free.
 *
 * NOTE ON OrderCancelledEvent:
 *   OrderService.cancelOrder() already publishes OrderCancelledEvent internally,
 *   so this job does NOT publish it again — that would trigger a double stock
 *   restoration for every abandoned order.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbandonedOrderCleanupJob {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT2M")
    @SchedulerLock(name = "abandonedOrderCleanup", lockAtMostFor = "PT9M", lockAtLeastFor = "PT4M")
    public void cancelAbandonedOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);

        List<Order> abandoned = orderRepository
                .findByOrderStatusAndCreatedAtBefore(OrderStatus.PENDING_PAYMENT, cutoff);

        if (abandoned.isEmpty()) {
            return;
        }

        log.info("Abandoned order cleanup: found {} stale PENDING_PAYMENT orders older than 30 minutes",
                abandoned.size());

        int cancelled = 0;
        for (Order order : abandoned) {
            try {
                // Route through OrderService so that:
                //   - validateStatusTransition() guards against invalid state changes
                //   - addStatusHistory() records the cancellation with reason + timestamp
                //   - OrderCancelledEvent is published exactly once (inside cancelOrder)
                //     which triggers async stock restoration
                orderService.cancelOrder(
                        order.getId(),
                        "Auto-cancelled: payment not received within 30 minutes"
                );

                log.info("Auto-cancelled abandoned order: {} (created: {})",
                        order.getOrderNumber(), order.getCreatedAt());
                cancelled++;

            } catch (IllegalStateException e) {
                // Order was in a state that cannot be cancelled (e.g., just shipped).
                // This is a legitimate race condition — log and skip, do not retry.
                log.warn("Skipping auto-cancel for order {} — invalid state transition: {}",
                        order.getOrderNumber(), e.getMessage());

            } catch (Exception e) {
                // Unexpected error — log and continue so one failure doesn't
                // block the rest of the batch from being cleaned up.
                log.error("Failed to auto-cancel order {}: {}",
                        order.getOrderNumber(), e.getMessage());
            }
        }

        log.info("Abandoned order cleanup complete: {}/{} orders cancelled",
                cancelled, abandoned.size());
    }
}