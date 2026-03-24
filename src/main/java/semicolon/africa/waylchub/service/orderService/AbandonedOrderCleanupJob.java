package semicolon.africa.waylchub.service.orderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.event.OrderCancelledEvent;
import semicolon.africa.waylchub.model.order.Order;
import semicolon.africa.waylchub.model.order.OrderStatus;
import semicolon.africa.waylchub.repository.orderRepository.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;

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
 * Without ShedLock, every pod would cancel the same orders concurrently.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbandonedOrderCleanupJob {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    // Run every 10 minutes. ShedLock holds a lock for 9 minutes so overlapping
    // runs are impossible even if a pod hangs.
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

        for (Order order : abandoned) {
            try {
                order.setOrderStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);

                // Fire the same event as manual cancellation — restores stock asynchronously
                eventPublisher.publishEvent(
                        new OrderCancelledEvent(order.getId(), order.getItems())
                );

                log.info("Auto-cancelled abandoned order: {} (created: {})",
                        order.getOrderNumber(), order.getCreatedAt());

            } catch (Exception e) {
                // Log and continue — don't let one failure stop the rest from being cleaned up
                log.error("Failed to auto-cancel order {}: {}", order.getOrderNumber(), e.getMessage());
            }
        }

        log.info("Abandoned order cleanup complete: {} orders cancelled", abandoned.size());
    }
}