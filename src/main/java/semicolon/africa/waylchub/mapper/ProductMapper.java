package semicolon.africa.waylchub.mapper;

import semicolon.africa.waylchub.dto.productDto.ProductRequestDTO;
import semicolon.africa.waylchub.dto.productDto.ProductResponseDto;
import semicolon.africa.waylchub.model.product.Product;

import java.time.LocalDateTime;

public class ProductMapper {

    public static ProductResponseDto toResponseDTO(Product product) {
        return ProductResponseDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .imageUrls(product.getImageUrls())
                .category(product.getCategory())
                .subCategory(product.getSubCategory())
                .quantityAvailable(product.getQuantityAvailable())
                .quantitySold(product.getQuantitySold())
                .brand(product.getBrand())
                .build();
    }

    public static Product toEntity(ProductRequestDTO dto) {
        Product product = new Product();
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setImageUrls(dto.getImageUrls());
        product.setCategory(dto.getCategory());
        product.setSubCategory(dto.getSubCategory());
        product.setQuantityAvailable(dto.getQuantityAvailable());
        product.setBrand(dto.getBrand());
        product.setBestSeller(dto.isBestSeller());
        product.setNewArrival(dto.isNewArrival());

        // Set default or system-managed fields
        product.setCreatedAt(LocalDateTime.now());
        product.setQuantitySold(0);
        product.setRating(0.0);

        return product;
    }
}
