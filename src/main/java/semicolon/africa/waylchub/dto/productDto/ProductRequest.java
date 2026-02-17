package semicolon.africa.waylchub.dto.productDto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import semicolon.africa.waylchub.model.product.ProductImage;

import java.math.BigDecimal;
import java.util.*;

@Data
public class ProductRequest {

    private String id; // Null for new, Present for update

    @NotNull(message = "Product name is required")
    private String name;

    @NotNull(message = "Slug is required")
    private String slug;

    @NotNull(message = "Base price is required")
    private BigDecimal basePrice; // Display price "From N5,000"
    private BigDecimal compareAtPrice;
    private BigDecimal discount;

    private String description;
    private Boolean isActive = true;


    // Static specs: {"Material": "Cotton", "Warranty": "1 Year"}
    private Map<String, String> specifications = new HashMap<>();

    // DEFINITIONS: {"Size": ["S", "M", "L"], "Color": ["Red", "Blue"]}
    private Map<String, List<String>> variantOptions = new HashMap<>();

    private List<ProductImage> images = new ArrayList<>();

    private String categorySlug;
    private String brandSlug;
}