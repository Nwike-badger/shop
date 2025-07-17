package semicolon.africa.waylchub.service.productService;

import semicolon.africa.waylchub.dto.productDto.*;

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
    List<ProductResponseDto> searchProducts(SearchProductRequest request);
}