package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.dto.productDto.*;
import semicolon.africa.waylchub.exception.InsufficientStockException;
import semicolon.africa.waylchub.mapper.ProductMapper;
import semicolon.africa.waylchub.model.product.*;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.exception.ProductNotFoundException;
import semicolon.africa.waylchub.exception.SkuAlreadyExistsException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;

    @Override
    public ProductResponseDto createProduct(CreateProductRequest request) {
        Optional<Product> existing = productRepository.findByNameAndCategory(request.getName(), request.getCategory());
        Product product;

        if (existing.isPresent()) {
            product = existing.get();
        } else {
            product = Product.builder()
                    .name(request.getName())
                    .description(request.getDescription())
                    .category(request.getCategory())
                    .subCategory(request.getSubCategory())
                    .tags(request.getTags())
                    .brand(request.getBrand()) // Map brand for new product
                    .createdAt(LocalDateTime.now())
                    .variants(new ArrayList<>())
                    .build();
        }

        // Use a temporary map to quickly check for duplicate SKUs within the current request
        Set<String> requestSkus = new HashSet<>();
        for (ProductVariantRequest variantRequest : request.getVariants()) {
            if (!requestSkus.add(variantRequest.getSku())) {
                throw new SkuAlreadyExistsException("Duplicate SKU '" + variantRequest.getSku() + "' found in the request payload.");
            }

            Optional<Product> existingSkuProduct = productRepository.findByVariants_Sku(variantRequest.getSku());

            if (existingSkuProduct.isPresent() && !existingSkuProduct.get().getId().equals(product.getId())) {
                throw new SkuAlreadyExistsException("SKU '" + variantRequest.getSku() + "' already exists for another product (ID: " + existingSkuProduct.get().getId() + ").");
            }

            // Remove existing variant by SKU to prepare for adding the updated one
            // This is a "replace or add" strategy for variants in the create/update flow
            product.getVariants().removeIf(v -> v.getSku().equals(variantRequest.getSku()));
            product.getVariants().add(ProductMapper.toProductVariant(variantRequest));
        }

        return ProductMapper.toProductResponseDto(productRepository.save(product));
    }

    @Override
    public ProductResponseDto getProductById(String id) {
        return ProductMapper.toProductResponseDto(
                productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id)));
    }

    @Override
    public ProductResponseDto getProductBySku(String sku) {
        return ProductMapper.toProductResponseDto(
                productRepository.findByVariants_Sku(sku).orElseThrow(() -> new ProductNotFoundException("Product variant with SKU '" + sku + "' not found.")));
    }

    @Override
    public List<ProductResponseDto> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductMapper::toProductResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public ProductResponseDto updateProduct(String id, CreateProductRequest request) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());
        product.setSubCategory(request.getSubCategory());
        product.setTags(request.getTags());
        product.setBrand(request.getBrand()); // Update brand

        // This update method for the whole product replaces or adds variants
        // based on the incoming request's variant list.
        // BE CAREFUL: If the request omits a variant, it will be removed from the product.
        // If you only intend to update specific variants, use dedicated variant update methods.
        List<ProductVariant> updatedVariants = new ArrayList<>();
        Set<String> requestSkus = new HashSet<>(); // For uniqueness check within the request
        for (ProductVariantRequest variantRequest : request.getVariants()) {
            if (!requestSkus.add(variantRequest.getSku())) {
                throw new SkuAlreadyExistsException("Duplicate SKU '" + variantRequest.getSku() + "' found in the request payload for update.");
            }

            Optional<Product> existingSkuProduct = productRepository.findByVariants_Sku(variantRequest.getSku());
            if (existingSkuProduct.isPresent() && !existingSkuProduct.get().getId().equals(id)) {
                throw new SkuAlreadyExistsException("SKU '" + variantRequest.getSku() + "' already exists for another product (ID: " + existingSkuProduct.get().getId() + ").");
            }
            updatedVariants.add(ProductMapper.toProductVariant(variantRequest));
        }
        product.setVariants(updatedVariants);

        return ProductMapper.toProductResponseDto(productRepository.save(product));
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
    public ProductResponseDto updateProductVariantQuantity(String sku, int quantityChange) {
        Product product = productRepository.findByVariants_Sku(sku).orElseThrow(() -> new ProductNotFoundException("Product variant with SKU '" + sku + "' not found."));

        Optional<ProductVariant> variantToUpdateOptional = product.getVariants().stream()
                .filter(v -> v.getSku().equals(sku))
                .findFirst();

        if (variantToUpdateOptional.isEmpty()) {
            // This case should ideally not be reached if findByVariants_Sku worked correctly,
            // but it's good for defensive programming.
            throw new ProductNotFoundException("Variant with SKU '" + sku + "' not found within product ID: " + product.getId());
        }

        ProductVariant variantToUpdate = variantToUpdateOptional.get();
        int updatedQty = variantToUpdate.getQuantity() + quantityChange;
        if (updatedQty < 0) {
            throw new InsufficientStockException("Insufficient stock for SKU: " + sku + ". Cannot reduce quantity below zero.");
        }
        variantToUpdate.setQuantity(updatedQty);

        return ProductMapper.toProductResponseDto(productRepository.save(product));
    }

    @Override
    public ProductResponseDto addVariantToProduct(String productId, ProductVariantRequest variantRequest) {
        Product product = productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + productId));

        // Check for SKU uniqueness across all products (important!)
        if (productRepository.findByVariants_Sku(variantRequest.getSku()).isPresent()) {
            throw new SkuAlreadyExistsException("SKU '" + variantRequest.getSku() + "' already exists for another product or variant.");
        }

        // Check for SKU uniqueness within the target product (if the product already has variants)
        if (product.getVariants().stream().anyMatch(v -> v.getSku().equals(variantRequest.getSku()))) {
            // This is an edge case if the global check passed but local didn't, implies data inconsistency
            throw new SkuAlreadyExistsException("SKU '" + variantRequest.getSku() + "' already exists within product ID: " + productId);
        }

        product.getVariants().add(ProductMapper.toProductVariant(variantRequest));
        return ProductMapper.toProductResponseDto(productRepository.save(product));
    }

    @Override
    public ProductResponseDto updateProductVariant(String productId, String sku, ProductVariantRequest variantRequest) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + productId));

        if (!sku.equals(variantRequest.getSku())) {
            // This check ensures consistency if SKU is passed in both path and body
            throw new IllegalArgumentException("SKU in path ('" + sku + "') does not match SKU in request body ('" + variantRequest.getSku() + "').");
        }

        Optional<ProductVariant> variantToUpdateOptional = product.getVariants().stream()
                .filter(v -> v.getSku().equals(sku))
                .findFirst();

        if (variantToUpdateOptional.isEmpty()) {
            throw new ProductNotFoundException("Variant with SKU '" + sku + "' not found within product ID: " + productId);
        }

        ProductVariant variantToUpdate = variantToUpdateOptional.get();

        // Update variant details
        variantToUpdate.setAttributes(variantRequest.getAttributes());
        variantToUpdate.setPrice(variantRequest.getPrice());
        variantToUpdate.setQuantity(variantRequest.getQuantity());
        variantToUpdate.setImageUrl(variantRequest.getImageUrl()); // Update imageUrl

        Product savedProduct = productRepository.save(product);
        return ProductMapper.toProductResponseDto(savedProduct);
    }

    @Override
    public void deleteProductVariant(String productId, String sku) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + productId));

        boolean removed = product.getVariants().removeIf(variant -> variant.getSku().equals(sku));

        if (!removed) {
            throw new ProductNotFoundException("Variant with SKU '" + sku + "' not found in product ID: " + productId);
        }


        productRepository.save(product);

    }
}