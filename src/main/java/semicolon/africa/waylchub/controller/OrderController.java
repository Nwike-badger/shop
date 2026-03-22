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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.orderDto.OrderRequest;
import semicolon.africa.waylchub.dto.orderDto.OrderResponse;
import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.order.Order;
import semicolon.africa.waylchub.service.orderService.OrderService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<?> placeOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody OrderRequest request) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Please log in to place an order"));
            }
            request.setCustomerEmail(userDetails.getUsername());
            request.setCustomerId(userDetails.getUserId());
            Order order = orderService.createOrder(request);
            return new ResponseEntity<>(mapToResponse(order), HttpStatus.CREATED);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Checkout failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/my-orders")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        if (userDetails == null) {
            throw new RuntimeException("Unauthenticated");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orders = orderService.getCustomerOrders(userDetails.getUserId(), pageable);
        return ResponseEntity.ok(orders.map(this::mapToResponse));
    }

    /**
     * Returns the full order document for the detail page.
     * Returns the complete Order (not the summarised OrderResponse) so the frontend
     * can display items, addresses, payment breakdown, status history, and tracking.
     */
    @GetMapping("/{orderNumber}")
    public ResponseEntity<?> getOrderByNumber(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String orderNumber) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Order order = orderService.getOrderByNumber(orderNumber);

        if (!order.getCustomerId().equals(userDetails.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
        }


        return ResponseEntity.ok(order);
    }

    @GetMapping("/verify/{orderId}")
    public ResponseEntity<?> verifyOrderStatus(@PathVariable String orderId) {
        try {
            Order order = orderService.getOrderById(orderId);
            return ResponseEntity.ok(Map.of("status", order.getOrderStatus().name()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }
    }

    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String orderId,
            @RequestBody(required = false) Map<String, String> payload) {
        try {
            if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

            Order existingOrder = orderService.getOrderById(orderId);
            if (!existingOrder.getCustomerId().equals(userDetails.getUserId())) {
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

    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerEmail(order.getCustomerEmail())
                .totalAmount(order.getGrandTotal())
                .orderStatus(order.getOrderStatus())
                .createdAt(order.getCreatedAt())
                .paymentStatus(order.getPaymentStatus())
                .itemNames(order.getItems() != null
                        ? order.getItems().stream()
                        .map(item -> item.getProductName() + " (x" + item.getQuantity() + ")")
                        .collect(Collectors.toList())
                        : List.of())
                .build();
    }
}