//package semicolon.africa.waylchub.service.productService;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import semicolon.africa.waylchub.dto.productDto.*; // Assuming cart DTOs are here, or change package
//import semicolon.africa.waylchub.exception.BadRequestException;
//import semicolon.africa.waylchub.exception.ResourceNotFoundException; // Consider renaming to ProductNotFoundException/CartNotFoundException
//import semicolon.africa.waylchub.model.product.Cart;
//import semicolon.africa.waylchub.model.product.CartItem;
//import semicolon.africa.waylchub.model.product.Product; // Import Product model
//import semicolon.africa.waylchub.model.product.ProductVariant; // Import ProductVariant model
//import semicolon.africa.waylchub.repository.productRepository.CartRepository;
//import semicolon.africa.waylchub.repository.productRepository.ProductRepository; // Inject ProductRepository if needed
//
//import java.math.BigDecimal;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Optional;
//import java.util.stream.Collectors;
//
//// Import your custom exceptions for better error handling
//import semicolon.africa.waylchub.exception.ProductNotFoundException;
//import semicolon.africa.waylchub.exception.CartNotFoundException; // New custom exception
//import semicolon.africa.waylchub.exception.InsufficientStockException;
//
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class CartServiceImpl implements CartService {
//
//    private final CartRepository cartRepository;
//    private final ProductService productService;
//    private final ProductRepository productRepository;
//
//    private static final int MAX_CART_ITEMS = 100; // Limit on unique item types, not total quantity
//
//    @Override
//    public CartResponse getCartByUserId(String userId) {
//        Optional<Cart> optionalCart = cartRepository.findByUserId(userId);
//        if (optionalCart.isEmpty()) {
//            Cart newCart = Cart.builder()
//                    .userId(userId)
//                    .items(new ArrayList<>())
//                    .totalPrice(BigDecimal.ZERO)
//                    .build();
//            cartRepository.save(newCart);
//            return mapToCartResponse(newCart, "New cart created successfully.");
//        }
//        return mapToCartResponse(optionalCart.get(), "Cart fetched successfully.");
//    }
//
//    @Override
//    public CartResponse addItemsToCart(AddToCartRequest request) {
//        String userId = request.getUserId();
//        List<CartItemDto> incomingItems = request.getItems();
//
//        Cart cart = cartRepository.findByUserId(userId)
//                .orElseGet(() -> Cart.builder()
//                        .userId(userId)
//                        .items(new ArrayList<>())
//                        .totalPrice(BigDecimal.ZERO)
//                        .build());
//
//        for (CartItemDto incomingItem : incomingItems) {
//            if (incomingItem.getQuantity() <= 0) {
//                throw new BadRequestException("Quantity must be greater than zero for SKU: " + incomingItem.getSku());
//            }
//
//            // 1. Find the product that contains this SKU
//            Product product = productRepository.findByVariants_Sku(incomingItem.getSku())
//                    .orElseThrow(() -> new ProductNotFoundException("Product with SKU '" + incomingItem.getSku() + "' not found."));
//
//            // 2. Find the specific variant within that product
//            ProductVariant variant = product.getVariants().stream()
//                    .filter(v -> v.getSku().equals(incomingItem.getSku()))
//                    .findFirst()
//                    .orElseThrow(() -> new ProductNotFoundException("Variant with SKU '" + incomingItem.getSku() + "' not found within product ID: " + product.getId()));
//
//            // Check if requested quantity exceeds available stock for this variant
//            if (incomingItem.getQuantity() > variant.getQuantity()) {
//                throw new InsufficientStockException("Requested quantity (" + incomingItem.getQuantity() + ") exceeds available stock (" + variant.getQuantity() + ") for SKU: " + incomingItem.getSku());
//            }
//
//            Optional<CartItem> existingCartItem = cart.getItems().stream()
//                    .filter(item -> item.getSku().equals(incomingItem.getSku())) // Filter by SKU now
//                    .findFirst();
//
//            if (existingCartItem.isPresent()) {
//                CartItem itemToUpdate = existingCartItem.get();
//                int updatedQuantity = itemToUpdate.getQuantity() + incomingItem.getQuantity();
//
//                if (updatedQuantity > variant.getQuantity()) { // Check against variant's stock
//                    throw new InsufficientStockException("Updated quantity (" + updatedQuantity + ") exceeds available stock (" + variant.getQuantity() + ") for SKU: " + incomingItem.getSku());
//                }
//
//                itemToUpdate.setQuantity(updatedQuantity);
//            } else {
//                if (cart.getItems().size() >= MAX_CART_ITEMS) {
//                    throw new BadRequestException("Cart item limit (" + MAX_CART_ITEMS + " unique items) exceeded.");
//                }
//
//                CartItem newCartItem = CartItem.builder()
//                        .productId(product.getId())
//                        .sku(variant.getSku())
//                        .name(product.getName()) // Use parent product's name
//                        .variantAttributes(variant.getAttributes()) // Store variant attributes
//                        .price(variant.getPrice()) // Use variant's price
//                        .quantity(incomingItem.getQuantity())
//                        .imageUrls(variant.getImageUrls()) // Use variant's image URL
//                        .build();
//
//                cart.getItems().add(newCartItem);
//            }
//        }
//
//        recalculateTotalPrice(cart);
//        Cart savedCart = cartRepository.save(cart);
//        return mapToCartResponse(savedCart, "Items added/updated in cart successfully.");
//    }
//
//    @Override
//    public CartResponse updateCartItemQuantity(UpdateCartItemRequest request) {
//        String userId = request.getUserId();
//        String sku = request.getSku(); // Changed to SKU
//        int newQuantity = request.getQuantity();
//
//        if (newQuantity < 0) {
//            throw new BadRequestException("Quantity cannot be negative for SKU: " + sku);
//        }
//
//        Cart cart = cartRepository.findByUserId(userId)
//                .orElseThrow(() -> new CartNotFoundException("Cart not found for user ID: " + userId));
//
//        // Find the product and its variant by SKU
//        Product product = productRepository.findByVariants_Sku(sku)
//                .orElseThrow(() -> new ProductNotFoundException("Product with SKU '" + sku + "' not found."));
//
//        ProductVariant variant = product.getVariants().stream()
//                .filter(v -> v.getSku().equals(sku))
//                .findFirst()
//                .orElseThrow(() -> new ProductNotFoundException("Variant with SKU '" + sku + "' not found within product ID: " + product.getId()));
//
//        if (newQuantity > variant.getQuantity()) { // Check against variant's stock
//            throw new InsufficientStockException("Requested quantity (" + newQuantity + ") exceeds available stock (" + variant.getQuantity() + ") for SKU: " + sku);
//        }
//
//        Optional<CartItem> optionalCartItem = cart.getItems().stream()
//                .filter(item -> item.getSku().equals(sku)) // Filter by SKU
//                .findFirst();
//
//        if (optionalCartItem.isEmpty()) {
//            throw new ProductNotFoundException("Product variant with SKU '" + sku + "' not found in cart.");
//        }
//
//        CartItem itemToUpdate = optionalCartItem.get();
//
//        if (newQuantity == 0) {
//            cart.getItems().remove(itemToUpdate);
//        } else {
//            itemToUpdate.setQuantity(newQuantity);
//        }
//
//        recalculateTotalPrice(cart);
//        Cart savedCart = cartRepository.save(cart);
//        return mapToCartResponse(savedCart, "Cart item quantity updated successfully.");
//    }
//
//    @Override
//    public CartResponse removeCartItem(String userId, String sku) { // Changed to SKU
//        Cart cart = cartRepository.findByUserId(userId)
//                .orElseThrow(() -> new CartNotFoundException("Cart not found for user ID: " + userId));
//
//        boolean removed = cart.getItems().removeIf(item -> item.getSku().equals(sku)); // Remove by SKU
//
//        if (!removed) {
//            throw new ProductNotFoundException("Product variant with SKU '" + sku + "' not found in cart.");
//        }
//
//        recalculateTotalPrice(cart);
//        Cart savedCart = cartRepository.save(cart);
//        return mapToCartResponse(savedCart, "Product variant removed from cart successfully.");
//    }
//
//    @Override
//    public CartResponse clearCart(String userId) {
//        Cart cart = cartRepository.findByUserId(userId)
//                .orElseThrow(() -> new CartNotFoundException("Cart not found for user ID: " + userId));
//
//        cart.getItems().clear();
//        cart.setTotalPrice(BigDecimal.ZERO);
//        Cart savedCart = cartRepository.save(cart);
//        return mapToCartResponse(savedCart, "Cart cleared successfully.");
//    }
//
//    private void recalculateTotalPrice(Cart cart) {
//        BigDecimal total = cart.getItems().stream()
//                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
//                .reduce(BigDecimal.ZERO, BigDecimal::add);
//        cart.setTotalPrice(total);
//    }
//
//    private CartResponse mapToCartResponse(Cart cart, String message) {
//        return CartResponse.builder()
//                .cartId(cart.getId())
//                .userId(cart.getUserId())
//                .items(cart.getItems())
//                .totalPrice(cart.getTotalPrice())
//                .message(message)
//                .build();
//    }
//}