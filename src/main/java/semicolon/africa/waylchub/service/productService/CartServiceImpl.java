package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.dto.productDto.*;
import semicolon.africa.waylchub.exception.BadRequestException;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.product.Cart;
import semicolon.africa.waylchub.model.product.CartItem;
import semicolon.africa.waylchub.repository.productRepository.CartRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductService productService;

    private static final int MAX_CART_ITEMS = 100;

    @Override
    public CartResponse getCartByUserId(String userId) {
        Optional<Cart> optionalCart = cartRepository.findByUserId(userId);
        if (optionalCart.isEmpty()) {
            Cart newCart = Cart.builder()
                    .userId(userId)
                    .items(new ArrayList<>())
                    .totalPrice(BigDecimal.ZERO)
                    .build();
            cartRepository.save(newCart);
            return mapToCartResponse(newCart, "New cart created successfully.");
        }
        return mapToCartResponse(optionalCart.get(), "Cart fetched successfully.");
    }

    @Override
    public CartResponse addItemsToCart(AddToCartRequest request) {
        String userId = request.getUserId();
        List<CartItemDto> incomingItems = request.getItems();

        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> Cart.builder()
                        .userId(userId)
                        .items(new ArrayList<>())
                        .totalPrice(BigDecimal.ZERO)
                        .build());

        for (CartItemDto incomingItem : incomingItems) {
            if (incomingItem.getQuantity() <= 0) {
                throw new BadRequestException("Quantity must be greater than zero.");
            }

            ProductResponseDto product = productService.getProductById(incomingItem.getProductId());

            if (incomingItem.getQuantity() > product.getQuantityAvailable()) {
                throw new BadRequestException("Requested quantity exceeds available stock for product: " + product.getName());
            }

            Optional<CartItem> existingCartItem = cart.getItems().stream()
                    .filter(item -> item.getProductId().equals(incomingItem.getProductId()))
                    .findFirst();

            if (existingCartItem.isPresent()) {
                CartItem itemToUpdate = existingCartItem.get();
                int updatedQuantity = itemToUpdate.getQuantity() + incomingItem.getQuantity();

                if (updatedQuantity > product.getQuantityAvailable()) {
                    throw new BadRequestException("Updated quantity exceeds available stock for product: " + product.getName());
                }

                itemToUpdate.setQuantity(updatedQuantity);
            } else {
                if (cart.getItems().size() >= MAX_CART_ITEMS) {
                    throw new BadRequestException("Cart item limit (" + MAX_CART_ITEMS + ") exceeded.");
                }

                CartItem newCartItem = CartItem.builder()
                        .productId(product.getId())
                        .name(product.getName())
                        .price(product.getPrice())
                        .quantity(incomingItem.getQuantity())
                        .imageUrls(product.getImageUrls())
                        .build();

                cart.getItems().add(newCartItem);
            }
        }

        recalculateTotalPrice(cart);
        Cart savedCart = cartRepository.save(cart);
        return mapToCartResponse(savedCart, "Items added/updated in cart successfully.");
    }

    @Override
    public CartResponse updateCartItemQuantity(UpdateCartItemRequest request) {
        String userId = request.getUserId();
        String productId = request.getProductId();
        int newQuantity = request.getQuantity();

        if (newQuantity < 0) {
            throw new BadRequestException("Quantity cannot be negative.");
        }

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user ID: " + userId));

        ProductResponseDto product = productService.getProductById(productId);

        if (newQuantity > product.getQuantityAvailable()) {
            throw new BadRequestException("Requested quantity exceeds available stock for product: " + product.getName());
        }

        Optional<CartItem> optionalCartItem = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst();

        if (optionalCartItem.isEmpty()) {
            throw new ResourceNotFoundException("Product with ID " + productId + " not found in cart.");
        }

        CartItem itemToUpdate = optionalCartItem.get();

        if (newQuantity == 0) {
            cart.getItems().remove(itemToUpdate);
        } else {
            itemToUpdate.setQuantity(newQuantity);
        }

        recalculateTotalPrice(cart);
        Cart savedCart = cartRepository.save(cart);
        return mapToCartResponse(savedCart, "Cart item quantity updated successfully.");
    }

    @Override
    public CartResponse removeCartItem(String userId, String productId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user ID: " + userId));

        boolean removed = cart.getItems().removeIf(item -> item.getProductId().equals(productId));

        if (!removed) {
            throw new ResourceNotFoundException("Product with ID " + productId + " not found in cart.");
        }

        recalculateTotalPrice(cart);
        Cart savedCart = cartRepository.save(cart);
        return mapToCartResponse(savedCart, "Product removed from cart successfully.");
    }

    @Override
    public CartResponse clearCart(String userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user ID: " + userId));

        cart.getItems().clear();
        cart.setTotalPrice(BigDecimal.ZERO);
        Cart savedCart = cartRepository.save(cart);
        return mapToCartResponse(savedCart, "Cart cleared successfully.");
    }

    private void recalculateTotalPrice(Cart cart) {
        BigDecimal total = cart.getItems().stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        cart.setTotalPrice(total);
    }

    private CartResponse mapToCartResponse(Cart cart, String message) {
        return CartResponse.builder()
                .cartId(cart.getId())
                .userId(cart.getUserId())
                .items(cart.getItems())
                .totalPrice(cart.getTotalPrice())
                .message(message)
                .build();
    }
}
