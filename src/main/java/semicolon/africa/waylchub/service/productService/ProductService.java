package semicolon.africa.waylchub.service.productService;

import semicolon.africa.waylchub.dto.productDto.CreateProductRequest;
import semicolon.africa.waylchub.dto.productDto.ProductResponseDto;
import semicolon.africa.waylchub.dto.productDto.ProductVariantRequest;

import java.util.List;

public interface ProductService {
    ProductResponseDto createProduct(CreateProductRequest request);
    ProductResponseDto getProductById(String id);
    ProductResponseDto getProductBySku(String sku);
    List<ProductResponseDto> getAllProducts();
    List<ProductResponseDto> getByCategory(String category);
    List<ProductResponseDto> getBySubCategory(String subCategory);
    ProductResponseDto updateProduct(String productId, CreateProductRequest request);
    void deleteProduct(String productId);

    ProductResponseDto updateProductVariantQuantity(String sku, int quantityChange);
    ProductResponseDto addVariantToProduct(String productId, ProductVariantRequest variantRequest);
    ProductResponseDto updateProductVariant(String productId, String sku, ProductVariantRequest variantRequest); // Added
    void deleteProductVariant(String productId, String sku); // Added
}