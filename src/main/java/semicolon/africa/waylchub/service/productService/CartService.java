package semicolon.africa.waylchub.service.productService;

import semicolon.africa.waylchub.dto.productDto.AddToCartRequest;
import semicolon.africa.waylchub.dto.productDto.CartResponse;
import semicolon.africa.waylchub.dto.productDto.UpdateCartItemRequest;
import semicolon.africa.waylchub.exception.InvalidCartOperationException;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;

public interface CartService {
    CartResponse getCartByUserId(String userId);
    CartResponse addItemsToCart(AddToCartRequest request) throws ResourceNotFoundException, InsufficientStockException;
    CartResponse updateCartItemQuantity(UpdateCartItemRequest request) throws ResourceNotFoundException, InvalidCartOperationException;
    CartResponse removeCartItem(String userId, String productId) throws ResourceNotFoundException;
    CartResponse clearCart(String userId) throws ResourceNotFoundException;
}
