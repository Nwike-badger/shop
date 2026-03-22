package semicolon.africa.waylchub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.orderDto.OrderItemRequest;
import semicolon.africa.waylchub.dto.productDto.cart.UpdateCartItemRequest;
import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails;
import semicolon.africa.waylchub.model.product.Cart;
import semicolon.africa.waylchub.service.productService.CartService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping("/add")
    public ResponseEntity<Cart> addToCart(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestHeader(value = "X-Guest-ID", required = false) String guestId,
            @Valid @RequestBody OrderItemRequest request) {

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

    /**
     * Updates the quantity of a specific item already in the cart.
     *
     * If quantity == 0 the frontend can call this and the service will remove the item,
     * rather than saving a zero-quantity row. The controller delegates both paths cleanly.
     *
     * Uses the same @AuthenticationPrincipal + X-Guest-ID pattern as every other endpoint —
     * no HttpServletRequest or manual token extraction needed.
     */
    @PutMapping("/update")
    public ResponseEntity<?> updateCartItem(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestHeader(value = "X-Guest-ID", required = false) String guestId,
            @Valid @RequestBody UpdateCartItemRequest request) {

        String userId = (user != null) ? user.getUserId() : null;

        if (userId == null && guestId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Guest ID required for unauthenticated users"));
        }

        // Delegate quantity=0 as a remove so the service stays clean
        if (request.getQuantity() < 1) {
            Cart cart = cartService.removeFromCart(userId, guestId, request.getVariantId());
            return ResponseEntity.ok(cart);
        }

        Cart cart = cartService.updateItemQuantity(userId, guestId, request.getVariantId(), request.getQuantity());
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/remove/{variantId}")
    public ResponseEntity<Cart> removeFromCart(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestHeader(value = "X-Guest-ID", required = false) String guestId,
            @PathVariable String variantId) {

        String userId = (user != null) ? user.getUserId() : null;
        return ResponseEntity.ok(cartService.removeFromCart(userId, guestId, variantId));
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearCart(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestHeader(value = "X-Guest-ID", required = false) String guestId) {

        String userId = (user != null) ? user.getUserId() : null;
        cartService.clearCart(userId, guestId);
        return ResponseEntity.noContent().build(); // 204 is more correct than 200 for a clear operation
    }
}