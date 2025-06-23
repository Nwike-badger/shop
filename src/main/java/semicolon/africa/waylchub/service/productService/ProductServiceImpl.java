package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.dto.productDto.ProductRequestDTO;
import semicolon.africa.waylchub.dto.productDto.ProductResponseDto;
import semicolon.africa.waylchub.mapper.ProductMapper;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;

import java.util.List;
import java.util.stream.Collectors;

import static semicolon.africa.waylchub.mapper.ProductMapper.toResponseDTO;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    public ProductResponseDto createProduct(ProductRequestDTO request) {
        Product product = ProductMapper.toEntity(request);
        Product saved = productRepository.save(product);
        return toResponseDTO(saved);
    }

    @Override
    public List<ProductResponseDto> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ProductResponseDto getProductById(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        return toResponseDTO(product);
    }

    @Override
    public List<ProductResponseDto> getByCategory(String category) {
        return productRepository.findByCategory(category).stream()
                .map(ProductMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductResponseDto> getBySubCategory(String subCategory) {
        return productRepository.findBySubCategory(subCategory).stream()
                .map(ProductMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ProductResponseDto updateProduct(String productId, ProductRequestDTO request) {
        Product existingProduct = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        existingProduct.setName(request.getName());
        existingProduct.setDescription(request.getDescription());
        existingProduct.setPrice(request.getPrice());
        existingProduct.setImageUrls(request.getImageUrls());
        existingProduct.setCategory(request.getCategory());
        existingProduct.setSubCategory(request.getSubCategory());
        existingProduct.setQuantityAvailable(request.getQuantityAvailable());
        existingProduct.setBrand(request.getBrand());
        existingProduct.setBestSeller(request.isBestSeller());
        existingProduct.setNewArrival(request.isNewArrival());

        Product updatedProduct = productRepository.save(existingProduct);
        return ProductMapper.toResponseDTO(updatedProduct);
    }

    @Override
    public void deleteProduct(String productId) {
        if (!productRepository.existsById(productId)) {
            throw new RuntimeException("Product not found");
        }
        productRepository.deleteById(productId);
    }


    // Optional (update these to return DTOs if needed)
    // @Override
    // public List<ProductResponseDTO> getBestSellers() {
    //     return productRepository.findByIsBestSellerTrue().stream()
    //         .map(ProductMapper::toResponseDTO)
    //         .collect(Collectors.toList());
    // }
    //
    // @Override
    // public List<ProductResponseDTO> getNewArrivals() {
    //     return productRepository.findByIsNewArrivalTrue().stream()
    //         .map(ProductMapper::toResponseDTO)
    //         .collect(Collectors.toList());
    // }
}
