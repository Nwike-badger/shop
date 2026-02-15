//package semicolon.africa.waylchub.controller;
//
//import jakarta.validation.Valid;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.web.bind.annotation.*;
//import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails;
//import semicolon.africa.waylchub.dto.orderDto.OrderRequest;
//import semicolon.africa.waylchub.dto.orderDto.OrderResponse;
//import semicolon.africa.waylchub.model.order.Order;
//import semicolon.africa.waylchub.service.orderService.OrderService;
//
//import java.util.stream.Collectors;
//
//@RestController
//@RequestMapping("/api/v1/orders")
//@RequiredArgsConstructor
//public class OrderController {
//
//    private final OrderService orderService;
//
//    @PostMapping
//    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest request) {
//
//        // 1. Get Authenticated User
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//
//        // Check if user is logged in
//        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
//        }
//
//        // 2. Extract Email/ID from Token (Safe way)
//        // If your CustomUserDetails holds the ID, use that.
//        // If not, use authentication.getName() which is usually the email/username.
//        String customerEmail;
//
//        if (authentication.getPrincipal() instanceof CustomUserDetails) {
//            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
//            customerEmail = userDetails.getUsername(); // Assuming username is email
//        } else {
//            // Fallback for standard Principal
//            customerEmail = authentication.getName();
//        }
//
//        // 3. Call Service with the secure email
//        Order order = orderService.placeOrder(request, customerEmail);
//
//        // 4. Build Response
//        OrderResponse response = OrderResponse.builder()
//                .orderId(order.getId()) // Ensure field match in DTO (id vs orderId)
//                .customerEmail(order.getCustomerEmail())
//                .totalAmount(order.getTotalAmount())
//                .status(order.getStatus())
//                .itemNames(order.getItems().stream()
//                        .map(item -> item.getProductName())
//                        .collect(Collectors.toList()))
//                .build();
//
//        return new ResponseEntity<>(response, HttpStatus.CREATED);
//    }
//}