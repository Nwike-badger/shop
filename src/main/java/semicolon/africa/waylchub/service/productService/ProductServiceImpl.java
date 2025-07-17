package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.dto.productDto.*;
import semicolon.africa.waylchub.exception.InsufficientStockException;
import semicolon.africa.waylchub.mapper.ProductMapper;
import semicolon.africa.waylchub.model.product.*;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.exception.ProductNotFoundException;
import semicolon.africa.waylchub.exception.SkuAlreadyExistsException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;

    @Override
    public ProductResponseDto createProduct(CreateProductRequest request) {
        log.info("Creating new product with SKU: {}", request.getSku());


        Optional<Product> existing = productRepository.findBySku(request.getSku());
        if (existing.isPresent()) {
            throw new SkuAlreadyExistsException("SKU '" + request.getSku() + "' already exists.");
        }

        LocalDateTime now = LocalDateTime.now();

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .category(request.getCategory())
                .subCategory(request.getSubCategory())
                .tags(request.getTags())
                .brand(request.getBrand())

                .sku(request.getSku())
                .attributes(request.getAttributes())
                .price(request.getPrice())
                .oldPrice(request.getOldPrice())
                .quantity(request.getQuantity())
                .imageUrls(request.getImageUrls())
                .discountPercentage(request.getDiscountPercentage())
                .discountColorCode(request.getDiscountColorCode())

                .totalReviews(request.getTotalReviews())
                .averageRating(request.getAverageRating())

                .createdAt(now)
                .updatedAt(now)
                .build();

        Product saved = productRepository.save(product);
        return ProductMapper.toProductResponseDto(saved);
    }

    @Override
    public ProductResponseDto getProductById(String id) {
        return ProductMapper.toProductResponseDto(
                productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id)));
    }

    @Override
    public ProductResponseDto getProductBySku(String sku) {
        return ProductMapper.toProductResponseDto(
                productRepository.findBySku(sku).orElseThrow(() -> new ProductNotFoundException("Product variant with SKU '" + sku + "' not found.")));
    }

    @Override
    public List<ProductResponseDto> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductMapper::toProductResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public ProductResponseDto updateProduct(String id, CreateProductRequest request) {
        log.info("Updating product with ID: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id));


        Optional<Product> skuConflict = productRepository.findBySku(request.getSku());
        if (skuConflict.isPresent() && !skuConflict.get().getId().equals(id)) {
            throw new SkuAlreadyExistsException("SKU '" + request.getSku() + "' already exists in another product.");
        }

        // Update fields
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());
        product.setSubCategory(request.getSubCategory());
        product.setTags(request.getTags());
        product.setBrand(request.getBrand());

        product.setSku(request.getSku());
        product.setAttributes(request.getAttributes());
        product.setPrice(request.getPrice());
        product.setOldPrice(request.getOldPrice());
        product.setQuantity(request.getQuantity());
        product.setImageUrls(request.getImageUrls());
        product.setDiscountPercentage(request.getDiscountPercentage());
        product.setDiscountColorCode(request.getDiscountColorCode());

        product.setTotalReviews(request.getTotalReviews());
        product.setAverageRating(request.getAverageRating());

        product.setUpdatedAt(LocalDateTime.now());

        Product saved = productRepository.save(product);
        return ProductMapper.toProductResponseDto(saved);
    }



    @Override
    public void deleteProduct(String id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException("Product not found with ID: " + id);
        }
        productRepository.deleteById(id);
    }

    @Override
    public List<ProductResponseDto> getByCategory(String category) {
        return productRepository.findByCategory(category).stream()
                .map(ProductMapper::toProductResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductResponseDto> getBySubCategory(String subCategory) {
        return productRepository.findBySubCategory(subCategory).stream()
                .map(ProductMapper::toProductResponseDto)
                .collect(Collectors.toList());
    }










    @Override
    public List<ProductResponseDto> searchProducts(SearchProductRequest request) {
        List<Product> products = productRepository.findAll();

        return products.stream()
                .filter(product -> {
                    // Filter by brand
                    if (request.getBrand() != null && !request.getBrand().equalsIgnoreCase(product.getBrand())) {
                        return false;
                    }

                    // Filter by tag
                    if (request.getTags() != null && !request.getTags().isEmpty()) {
                        boolean hasMatchingTag = product.getTags() != null &&
                                product.getTags().stream().anyMatch(tag -> request.getTags().contains(tag));
                        if (!hasMatchingTag) return false;
                    }

                    // Filter by price range (now on product level, not variant)
                    if (request.getMinPrice() != null && product.getPrice().compareTo(request.getMinPrice()) < 0) {
                        return false;
                    }

                    if (request.getMaxPrice() != null && product.getPrice().compareTo(request.getMaxPrice()) > 0) {
                        return false;
                    }

                    return true;
                })
                .map(ProductMapper::toProductResponseDto)
                .collect(Collectors.toList());
    }

}