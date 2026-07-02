package semicolon.africa.waylchub.service.orderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.dto.paymentDto.PaymentVerificationResult;
import semicolon.africa.waylchub.model.order.Order;
import semicolon.africa.waylchub.model.order.OrderStatus;
import semicolon.africa.waylchub.repository.orderRepository.OrderRepository;
import semicolon.africa.waylchub.service.paymentService.PaymentGatewayService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Auto-cancels PENDING_PAYMENT orders older than 30 minutes
 * — but only after checking the gateway of record first.
 *
 * WHY THE VERIFY STEP:
 *   Webhooks CAN be dropped. Without asking the gateway first, we would
 *   cancel orders that the customer actually paid for. Verify before cancel
 *   closes that hole. See production-incident notes for the full timeline.
 *
 * WHY ROUTE THROUGH OrderService:
 *   State-machine validation + audit history + single OrderCancelledEvent
 *   publish (which triggers async stock restoration).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbandonedOrderCleanupJob {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final Map<String, PaymentGatewayService> gateways;

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT2M")
    @SchedulerLock(name = "abandonedOrderCleanup", lockAtMostFor = "PT9M", lockAtLeastFor = "PT4M")
    public void cancelAbandonedOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        List<Order> abandoned = orderRepository
                .findByOrderStatusAndCreatedAtBefore(OrderStatus.PENDING_PAYMENT, cutoff);

        if (abandoned.isEmpty()) return;

        log.info("Cleanup: found {} stale PENDING_PAYMENT orders older than 30 minutes",
                abandoned.size());

        int recovered = 0, cancelled = 0, skipped = 0;

        for (Order order : abandoned) {
            String gatewayName = order.getPaymentGateway();

            // ── Step 1: If we know the gateway, verify with them first ────────
            if (gatewayName != null && !gatewayName.isBlank()) {
                PaymentGatewayService gw = gateways.get(gatewayName);
                if (gw != null) {
                    try {
                        PaymentVerificationResult r = gw.verifyTransaction(order.getId());

                        if (r.getStatus() == PaymentVerificationResult.Status.SUCCESSFUL) {
                            // Customer paid, webhook missed — save them
                            log.warn("Cleanup: RECOVERED missed payment — orderId={} gateway={}",
                                    order.getId(), gatewayName);
                            orderService.processSuccessfulPayment(
                                    order.getId(),
                                    r.getGatewayReference() != null ? r.getGatewayReference() : order.getId(),
                                    r.getPaymentMethod() != null ? r.getPaymentMethod() : gatewayName);
                            recovered++;
                            continue;   // Do NOT cancel
                        }

                        if (r.getStatus() == PaymentVerificationResult.Status.PENDING) {
                            // Customer might still be on the checkout page — leave it alone this round
                            log.info("Cleanup: order {} still PENDING at gateway — skipping", order.getId());
                            skipped++;
                            continue;
                        }
                        // FAILED or NOT_FOUND → fall through to cancel
                    } catch (Exception e) {
                        // Gateway itself is down. Safer to skip than to cancel a possibly-paid order.
                        log.warn("Cleanup: verify failed for order {} on {} — skipping this round: {}",
                                order.getId(), gatewayName, e.getMessage());
                        skipped++;
                        continue;
                    }
                }
            }

            // ── Step 2: Cancel and restore stock ──────────────────────────────
            try {
                orderService.cancelOrder(
                        order.getId(),
                        "Auto-cancelled: payment not received within 30 minutes");
                cancelled++;
                log.info("Auto-cancelled abandoned order: {} (created: {})",
                        order.getOrderNumber(), order.getCreatedAt());
            } catch (IllegalStateException e) {
                log.warn("Skipping auto-cancel for order {} — invalid state transition: {}",
                        order.getOrderNumber(), e.getMessage());
            } catch (Exception e) {
                log.error("Failed to auto-cancel order {}: {}",
                        order.getOrderNumber(), e.getMessage());
            }
        }

        log.info("Cleanup complete: {} recovered, {} cancelled, {} skipped (out of {})",
                recovered, cancelled, skipped, abandoned.size());
    }
}