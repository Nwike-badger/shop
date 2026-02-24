package semicolon.africa.waylchub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.orderDto.OrderRequest;
import semicolon.africa.waylchub.dto.orderDto.OrderResponse;
import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.order.Order;
import semicolon.africa.waylchub.service.orderService.OrderService;
// IMPORTANT: Adjust this import to match your actual CustomUserDetails location

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 🛒 SECURE CHECKOUT ENDPOINT
     * POST /api/v1/orders
     */
    @PostMapping
    public ResponseEntity<?> placeOrder(@Valid @RequestBody OrderRequest request) {
        try {
            // 1. Get Authenticated User
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in to place an order"));
            }

            // 2. Extract User Details Securely
            String customerEmail;
            String customerId = null;

            if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
                customerEmail = userDetails.getUsername();
                // If your CustomUserDetails has a getId() method, uncomment the next line:
                // customerId = userDetails.getId();
            } else {
                customerEmail = authentication.getName();
            }

            // 3. Override payload with trusted security context data
            request.setCustomerEmail(customerEmail);
            if (customerId != null) {
                request.setCustomerId(customerId);
            }

            // 4. Create Order
            Order order = orderService.createOrder(request);

            // 5. Build Safe Response
            OrderResponse response = mapToResponse(order);
            return new ResponseEntity<>(response, HttpStatus.CREATED);

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Checkout failed for user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 🧾 GET SINGLE ORDER (For Receipt Page)
     * GET /api/v1/orders/{orderNumber}
     */
    @GetMapping("/{orderNumber}")
    public ResponseEntity<?> getOrderByNumber(@PathVariable String orderNumber) {
        Order order = orderService.getOrderByNumber(orderNumber);

        // Security Check: Ensure the user owns this order
        String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!order.getCustomerEmail().equalsIgnoreCase(currentUserEmail)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }

        return ResponseEntity.ok(order);
    }

    /**
     * 📜 GET CUSTOMER ORDER HISTORY
     * GET /api/v1/orders/history
     */
    @GetMapping("/history")
    public ResponseEntity<Page<Order>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        // Extract ID/Email securely from context, not from the URL!
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Note: If you are searching by customerId in MongoDB, extract the ID here.
        // For now, we assume your repository can search by the authenticated name/email.
        String customerId = authentication.getName();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orders = orderService.getCustomerOrders(customerId, pageable);

        return ResponseEntity.ok(orders);
    }

    /**
     * 🛑 CANCEL ORDER
     * PATCH /api/v1/orders/{orderId}/cancel
     */
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable String orderId,
            @RequestBody(required = false) Map<String, String> payload) {
        try {
            // Security Check: Ensure the user owns this order before cancelling
            Order existingOrder = orderService.getOrderById(orderId);
            String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();

            if (!existingOrder.getCustomerEmail().equalsIgnoreCase(currentUserEmail)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
            }

            String reason = (payload != null && payload.containsKey("reason"))
                    ? payload.get("reason")
                    : "Cancelled by customer";

            Order cancelledOrder = orderService.cancelOrder(orderId, reason);
            return ResponseEntity.ok(mapToResponse(cancelledOrder));

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    // --- Helper Method ---
    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerEmail(order.getCustomerEmail())
                .grandTotal(order.getGrandTotal())
                .status(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .itemNames(order.getItems().stream()
                        .map(item -> item.getProductName() + " (x" + item.getQuantity() + ")")
                        .collect(Collectors.toList()))
                .build();
    }
}