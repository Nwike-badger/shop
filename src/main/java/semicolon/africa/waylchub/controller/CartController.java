package semicolon.africa.waylchub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.orderDto.OrderItemRequest;
import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails;
import semicolon.africa.waylchub.model.product.Cart;
import semicolon.africa.waylchub.service.productService.CartService;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping("/add")
    public ResponseEntity<Cart> addToCart(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestHeader(value = "X-Guest-ID", required = false) String guestId,
            @RequestBody OrderItemRequest request) {

        String userId = (user != null) ? user.getUserId() : null;

        if (userId == null && guestId == null) {
            throw new IllegalArgumentException("Guest ID required for unauthenticated users");
        }

        return ResponseEntity.ok(cartService.addToCart(userId, guestId, request));
    }

    @GetMapping
    public ResponseEntity<Cart> getCart(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestHeader(value = "X-Guest-ID", required = false) String guestId) {

        String userId = (user != null) ? user.getUserId() : null;
        return ResponseEntity.ok(cartService.getCart(userId, guestId));
    }

    // ✅ NEW: Remove Single Item
    @DeleteMapping("/remove/{variantId}")
    public ResponseEntity<Cart> removeFromCart(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestHeader(value = "X-Guest-ID", required = false) String guestId,
            @PathVariable String variantId) {

        String userId = (user != null) ? user.getUserId() : null;
        return ResponseEntity.ok(cartService.removeFromCart(userId, guestId, variantId));
    }

    // ✅ NEW: Clear Entire Cart
    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearCart(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestHeader(value = "X-Guest-ID", required = false) String guestId) {

        String userId = (user != null) ? user.getUserId() : null;
        cartService.clearCart(userId, guestId);
        return ResponseEntity.ok().build();
    }
}