package semicolon.africa.waylchub.dto.productDto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ProductRequest {
    @NotNull(message = "Product name is required")
    private String name;

    @NotNull(message = "Slug is required")
    private String slug;

    @NotNull(message = "Price is required")
    private BigDecimal price;

    private Integer stockQuantity;

    // We send Slugs from frontend, Service converts to DBRef
    private String categorySlug;
    private String brandSlug;

    private List<ProductAttributeRequest> attributes;
}
