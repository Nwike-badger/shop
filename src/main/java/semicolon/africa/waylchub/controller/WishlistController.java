package semicolon.africa.waylchub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails;
import semicolon.africa.waylchub.model.product.Wishlist;
import semicolon.africa.waylchub.service.productService.WishlistService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public ResponseEntity<Wishlist> getWishlist(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestHeader(value = "X-Guest-ID", required = false) String guestId) {

        String userId = (user != null) ? user.getUserId() : null;
        Wishlist wishlist = wishlistService.getWishlist(userId, guestId);

        // Return empty wishlist instead of null for cleaner frontend handling
        if (wishlist == null) {
            wishlist = new Wishlist();
        }
        return ResponseEntity.ok(wishlist);
    }

    @PostMapping("/add/{productId}")
    public ResponseEntity<?> addToWishlist(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestHeader(value = "X-Guest-ID", required = false) String guestId,
            @PathVariable String productId) {

        String userId = (user != null) ? user.getUserId() : null;

        if (userId == null && guestId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Guest ID required for unauthenticated users"));
        }

        return ResponseEntity.ok(wishlistService.addToWishlist(userId, guestId, productId));
    }

    @DeleteMapping("/remove/{productId}")
    public ResponseEntity<Wishlist> removeFromWishlist(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestHeader(value = "X-Guest-ID", required = false) String guestId,
            @PathVariable String productId) {

        String userId = (user != null) ? user.getUserId() : null;
        return ResponseEntity.ok(wishlistService.removeFromWishlist(userId, guestId, productId));
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearWishlist(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestHeader(value = "X-Guest-ID", required = false) String guestId) {

        String userId = (user != null) ? user.getUserId() : null;
        wishlistService.clearWishlist(userId, guestId);
        return ResponseEntity.noContent().build();
    }
}