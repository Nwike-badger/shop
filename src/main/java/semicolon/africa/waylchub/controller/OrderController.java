package semicolon.africa.waylchub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails; // Assuming you have this
import semicolon.africa.waylchub.dto.orderDto.OrderRequest;
import semicolon.africa.waylchub.dto.orderDto.OrderResponse;
import semicolon.africa.waylchub.service.orderService.OrderService;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String userId = userDetails.getUserId();

        OrderResponse response = orderService.placeOrder(request, userId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // Example: Add endpoint to get orders by user (protected)
    /*
    @GetMapping("/my-orders")
    public ResponseEntity<List<OrderResponse>> getUserOrders() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String userId = userDetails.getUserId();

        List<OrderResponse> userOrders = orderService.getUserOrders(userId);
        return ResponseEntity.ok(userOrders);
    }
    */
}
