package semicolon.africa.waylchub.service.productService;

import semicolon.africa.waylchub.dto.productDto.ProductRequestDTO;
import semicolon.africa.waylchub.dto.productDto.ProductResponseDto;

import java.util.List;

public interface ProductService {
    ProductResponseDto createProduct(ProductRequestDTO request);
    List<ProductResponseDto> getAllProducts();
    ProductResponseDto getProductById(String id);
    List<ProductResponseDto> getByCategory(String category);
    List<ProductResponseDto> getBySubCategory(String subCategory);
    ProductResponseDto updateProduct(String productId, ProductRequestDTO request);
    void deleteProduct(String productId);
//    List<Product> getBestSellers();
//    List<Product> getNewArrivals();
}
