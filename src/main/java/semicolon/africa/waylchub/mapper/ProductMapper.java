package semicolon.africa.waylchub.mapper;

import semicolon.africa.waylchub.dto.productDto.*;
import semicolon.africa.waylchub.model.product.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ProductMapper {
    public static ProductResponseDto toProductResponseDto(Product product) {
        if (product == null) return null;
        List<ProductVariantResponseDto> variants = product.getVariants() != null
                ? product.getVariants().stream().map(ProductMapper::toProductVariantResponseDto).collect(Collectors.toList())
                : Collections.emptyList();
        return ProductResponseDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .category(product.getCategory())
                .subCategory(product.getSubCategory())
                .tags(product.getTags())
                .brand(product.getBrand()) // Map brand
                .variants(variants)
                .createdAt(product.getCreatedAt())
                .build();
    }

    public static ProductVariantResponseDto toProductVariantResponseDto(ProductVariant variant) {
        if (variant == null) return null; // Added null check for robustness
        return new ProductVariantResponseDto(
                variant.getSku(),
                variant.getAttributes(),
                variant.getPrice(),
                variant.getQuantity(),
                variant.getImageUrl() // Map imageUrl
        );
    }

    // Helper to convert request variant to model variant
    public static ProductVariant toProductVariant(ProductVariantRequest request) {
        if (request == null) return null; // Added null check for robustness
        return new ProductVariant(
                request.getSku(),
                request.getAttributes(),
                request.getPrice(),
                request.getQuantity(),
                request.getImageUrl() // Map imageUrl
        );
    }
}