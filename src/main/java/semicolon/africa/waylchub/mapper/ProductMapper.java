package semicolon.africa.waylchub.mapper;

import semicolon.africa.waylchub.dto.productDto.CreateProductRequest;
import semicolon.africa.waylchub.dto.productDto.ProductResponseDto;
import semicolon.africa.waylchub.model.product.Product;

import java.time.LocalDateTime;

public class ProductMapper {

    public static Product toProduct(CreateProductRequest request) {
        LocalDateTime now = LocalDateTime.now();

        return Product.builder()
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
    }

    public static ProductResponseDto toProductResponseDto(Product product) {
        ProductResponseDto dto = new ProductResponseDto();

        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setCategory(product.getCategory());
        dto.setSubCategory(product.getSubCategory());
        dto.setTags(product.getTags());
        dto.setBrand(product.getBrand());

        dto.setSku(product.getSku());
        dto.setAttributes(product.getAttributes());
        dto.setPrice(product.getPrice());
        dto.setOldPrice(product.getOldPrice());
        dto.setQuantity(product.getQuantity());
        dto.setImageUrls(product.getImageUrls());
        dto.setDiscountPercentage(product.getDiscountPercentage());
        dto.setDiscountColorCode(product.getDiscountColorCode());

        dto.setTotalReviews(product.getTotalReviews());
        dto.setAverageRating(product.getAverageRating());

        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());

        return dto;
    }
}
