package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.orderDto.OrderItemRequest;
import semicolon.africa.waylchub.exception.InsufficientStockException;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.product.Cart;
import semicolon.africa.waylchub.model.product.CartItem;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.model.product.ProductVariant;
import semicolon.africa.waylchub.repository.productRepository.CartRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductVariantRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductService productService;
    private static final int MAX_CART_ITEMS = 50;

    /**
     * Adds an item to the cart.
     * Works for both Logged-in Users (userId != null) and Guests (sessionId != null).
     */
    @Transactional
    public Cart addToCart(String userId, String sessionId, OrderItemRequest request) {
        Cart cart = getCart(userId, sessionId);

        if (cart == null) {
            cart = Cart.builder()
                    .userId(userId)
                    // Only use sessionId if user is NOT logged in.
                    // This prevents "Zombie sessions" attached to logged-in users.
                    .sessionId(userId == null ? sessionId : null)
                    .items(new ArrayList<>())
                    .totalAmount(BigDecimal.ZERO)
                    .build();
        }

        // 🛡️ SECURITY: Validates Product Status, Stock, and Limits
        addProductToCart(cart, request);

        updateCartTotal(cart);
        // Update timestamp for TTL (Time-To-Live) cleanup
        cart.setUpdatedAt(LocalDateTime.now());

        return cartRepository.save(cart);
    }

    private void addProductToCart(Cart cart, OrderItemRequest request) {
        ProductVariant variant = variantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));

        Product product = productService.getProductById(variant.getProductId());

        // 🛡️ SECURITY: Prevent buying deactivated items
        if (!variant.isActive() || (product != null && !product.isActive())) {
            throw new InsufficientStockException("Product is no longer available");
        }

        // 🛡️ SECURITY: Constraint Check (Bot Protection)
        // We check distinct items size. If you want to limit total quantity, check that instead.
        if (cart.getItems().size() >= MAX_CART_ITEMS) {
            // Allow updating existing items, but block new ones
            boolean isNewItem = cart.getItems().stream()
                    .noneMatch(i -> i.getVariantId().equals(variant.getId()));

            if (isNewItem) {
                throw new IllegalArgumentException("Cart is full (Max " + MAX_CART_ITEMS + " unique items).");
            }
        }

        Optional<CartItem> existingItemOpt = cart.getItems().stream()
                .filter(item -> item.getVariantId().equals(variant.getId()))
                .findFirst();

        if (existingItemOpt.isPresent()) {
            // --- MERGE EXISTING ---
            CartItem existingItem = existingItemOpt.get();
            int newQuantity = existingItem.getQuantity() + request.getQuantity();

            // 🛡️ SECURITY: Strict Stock Check
            if (variant.isManageStock() && variant.getStockQuantity() < newQuantity) {
                throw new InsufficientStockException("Cannot add more. Max available is " + variant.getStockQuantity());
            }

            existingItem.setQuantity(newQuantity);
            // 🛡️ SECURITY: Always overwrite price from DB. Ignores frontend/stale data.
            existingItem.setUnitPrice(variant.getPrice());
            existingItem.setSubTotal(variant.getPrice().multiply(BigDecimal.valueOf(newQuantity)));

        } else {
            // --- ADD NEW ---
            if (variant.isManageStock() && variant.getStockQuantity() < request.getQuantity()) {
                throw new InsufficientStockException("Only " + variant.getStockQuantity() + " items left in stock");
            }

            String imageUrl = (variant.getImages() != null && !variant.getImages().isEmpty())
                    ? variant.getImages().get(0).getUrl() : null;

            CartItem newItem = CartItem.builder()
                    .productId(product.getId())
                    .variantId(variant.getId())
                    .productName(product.getName())
                    .sku(variant.getSku())
                    .imageUrl(imageUrl)
                    .quantity(request.getQuantity())
                    // 🛡️ SECURITY: Source of Truth = Database Price
                    .unitPrice(variant.getPrice())
                    .subTotal(variant.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())))
                    .build();

            cart.getItems().add(newItem);
        }
    }

    @Transactional
    public void mergeCarts(String guestSessionId, String userId) {
        if (guestSessionId == null) return;

        Optional<Cart> guestCartOpt = cartRepository.findBySessionId(guestSessionId);
        Optional<Cart> userCartOpt = cartRepository.findByUserId(userId);

        if (guestCartOpt.isEmpty()) return;

        Cart guestCart = guestCartOpt.get();

        if (userCartOpt.isPresent()) {
            Cart userCart = userCartOpt.get();
            log.info("Merging guest cart {} into user cart {}", guestSessionId, userId);

            for (CartItem guestItem : guestCart.getItems()) {
                Optional<CartItem> existingUserItem = userCart.getItems().stream()
                        .filter(i -> i.getVariantId().equals(guestItem.getVariantId()))
                        .findFirst();

                if (existingUserItem.isPresent()) {
                    CartItem existing = existingUserItem.get();
                    int newQuantity = existing.getQuantity() + guestItem.getQuantity();

                    // ✅ VALIDATION: Re-check stock during merge
                    // Fetch variant again to be 100% sure of stock levels
                    ProductVariant variant = variantRepository.findById(existing.getVariantId()).orElse(null);

                    if (variant != null && variant.isManageStock()) {
                        // If merge exceeds stock, cap it at max available
                        if (newQuantity > variant.getStockQuantity()) {
                            newQuantity = variant.getStockQuantity();
                        }
                    }
                    existing.setQuantity(newQuantity);
                } else {
                    userCart.getItems().add(guestItem);
                }
            }
            updateCartTotal(userCart);
            userCart.setUpdatedAt(LocalDateTime.now());
            cartRepository.save(userCart);
            cartRepository.delete(guestCart); // Cleanup guest data
        } else {
            // Case 2: User has NO cart -> Adopt the Guest cart
            log.info("Assigning guest cart {} to user {}", guestSessionId, userId);
            guestCart.setUserId(userId);
            guestCart.setSessionId(null); // Clear session ID
            guestCart.setUpdatedAt(LocalDateTime.now());
            cartRepository.save(guestCart);
        }
    }

    @Transactional
    public Cart getCart(String userId, String sessionId) {
        Optional<Cart> cartOpt;
        if (userId != null) {
            cartOpt = cartRepository.findByUserId(userId);
        } else {
            cartOpt = cartRepository.findBySessionId(sessionId);
        }

        return cartOpt.map(cart -> {
            // OPTIONAL: Re-validate prices on every fetch?
            // Usually overkill. We validate at Checkout.
            // But we DO recalculate totals just in case.
            updateCartTotal(cart);
            return cart;
        }).orElse(null);
    }

    private void updateCartTotal(Cart cart) {
        BigDecimal total = cart.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Ensure subTotals consistency
        cart.getItems().forEach(item ->
                item.setSubTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
        );

        cart.setTotalAmount(total);
    }

    @Transactional
    public Cart removeFromCart(String userId, String sessionId, String variantId) {
        Cart cart = getCart(userId, sessionId);
        if (cart == null) throw new ResourceNotFoundException("Cart not found");

        boolean removed = cart.getItems().removeIf(item -> item.getVariantId().equals(variantId));

        if (!removed) {
            throw new ResourceNotFoundException("Item not found in cart");
        }

        updateCartTotal(cart);
        return cartRepository.save(cart);
    }

    @Transactional
    public void clearCart(String userId, String sessionId) {
        Cart cart = getCart(userId, sessionId);
        if (cart != null) {
            cart.getItems().clear();
            cart.setTotalAmount(BigDecimal.ZERO);
            cartRepository.save(cart);
        }
    }


}