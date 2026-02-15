package semicolon.africa.waylchub.service.productService;

import semicolon.africa.waylchub.dto.productDto.cart.AddToCartRequest;
import semicolon.africa.waylchub.dto.productDto.cart.CartResponse;
import semicolon.africa.waylchub.dto.productDto.cart.UpdateCartItemRequest;
import semicolon.africa.waylchub.exception.InsufficientStockException;
import semicolon.africa.waylchub.exception.InvalidCartOperationException;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;

public interface CartService {
    CartResponse getCartByUserId(String userId);
    CartResponse addItemsToCart(AddToCartRequest request) throws ResourceNotFoundException, InsufficientStockException;
    CartResponse updateCartItemQuantity(UpdateCartItemRequest request) throws ResourceNotFoundException, InvalidCartOperationException;
    CartResponse removeCartItem(String userId, String productId) throws ResourceNotFoundException;
    CartResponse clearCart(String userId) throws ResourceNotFoundException;
}
