package semicolon.africa.waylchub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.model.order.*;
import semicolon.africa.waylchub.service.orderService.OrderService;

import java.util.Map;

/**
 * Admin-only order management controller.
 *
 * All endpoints are secured with ROLE_ADMIN at the class level.
 * Base path: /api/admin/orders  (matches the frontend's api.get('/admin/orders'))
 *
 * Note on cancel: The customer OrderController at /api/v1/orders/{id}/cancel
 * enforces ownership (customerId check). This admin endpoint bypasses that check
 * intentionally — admins can cancel any order regardless of owner.
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    /* ══════════════════════════════════════════════════════
       LIST — paginated, sortable, optionally filtered
    ══════════════════════════════════════════════════════ */

    /**
     * GET /api/admin/orders
     * Params: page, size, sortBy, direction, status (optional)
     *
     * Used by:
     *  - AdminOrders.jsx    → full table
     *  - useOrderNotifications.js → poll (page=0, size=20, sort=createdAt,desc)
     */
    @GetMapping
    public ResponseEntity<Page<Order>> getAllOrders(
            @RequestParam(defaultValue = "0")         int page,
            @RequestParam(defaultValue = "20")        int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc")      String direction,
            @RequestParam(required = false)           OrderStatus status) {

        Sort sort     = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Order> orders = (status != null)
                ? orderService.getOrdersByStatus(status, pageable)
                : orderService.getAllOrders(pageable);

        return ResponseEntity.ok(orders);
    }

    /* ══════════════════════════════════════════════════════
       DETAIL
    ══════════════════════════════════════════════════════ */

    /**
     * GET /api/admin/orders/{orderId}
     * Returns the full Order document (items, addresses, history, financials).
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrderById(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.getOrderById(orderId));
    }

    /* ══════════════════════════════════════════════════════
       STATUS TRANSITION (advance: PROCESSING → CONFIRMED etc.)
    ══════════════════════════════════════════════════════ */

    /**
     * PATCH /api/admin/orders/{orderId}/status
     * Body: { "status": "SHIPPED", "note": "Dispatched via DHL" }
     *
     * The OrderService.updateOrderStatus() enforces the state machine —
     * invalid transitions return 400 with a descriptive message.
     *
     * Used by OrderDetailPanel advance-status buttons.
     */
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable String orderId,
            @RequestBody Map<String, String> body) {
        try {
            String rawStatus = body.get("status");
            if (rawStatus == null || rawStatus.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "status field is required"));
            }

            OrderStatus newStatus = OrderStatus.valueOf(rawStatus.trim().toUpperCase());
            String note = body.getOrDefault("note", "Status updated by admin");

            Order updated = orderService.updateOrderStatus(orderId, newStatus, note);
            return ResponseEntity.ok(updated);

        } catch (IllegalArgumentException e) {
            // OrderStatus.valueOf() failed — unknown status string
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid status value: " + body.get("status")));
        } catch (IllegalStateException e) {
            // State machine rejected the transition
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* ══════════════════════════════════════════════════════
       CANCEL (admin bypass — no ownership check)
    ══════════════════════════════════════════════════════ */

    /**
     * PATCH /api/admin/orders/{orderId}/cancel
     * Body: { "reason": "Customer requested cancellation" }
     *
     * ✅ FIX: OrderDetailPanel was incorrectly calling the CUSTOMER endpoint
     *    (/api/v1/orders/{id}/cancel) which enforces customerId ownership.
     *    Admin cancellation must come here instead.
     *
     * Stock is restored via OrderCancelledEvent (published inside cancelOrder()).
     */
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable String orderId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String reason = (body != null && body.containsKey("reason"))
                    ? body.get("reason")
                    : "Cancelled by admin";

            Order cancelled = orderService.cancelOrder(orderId, reason);
            return ResponseEntity.ok(cancelled);

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}