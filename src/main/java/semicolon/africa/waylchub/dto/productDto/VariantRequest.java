package semicolon.africa.waylchub.dto.productDto;


import lombok.Data;
import semicolon.africa.waylchub.model.product.ProductImage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class VariantRequest {

    // Null when creating, present when updating
    private String id;

    // Parent product
    private String productId;

    // Each variant must have unique SKU
    private String sku;

    // Optional override price
    private BigDecimal price;

    // Optional compare-at price
    private BigDecimal compareAtPrice;

    // Inventory for this specific variant
    private Integer stockQuantity;

    /**
     * Flexible attribute map
     * Example:
     * {
     *   "Size": "M",
     *   "Color": "Black",
     *   "Storage": "128GB"
     * }
     */
    private Map<String, String> attributes = new HashMap<>();

    /**
     * Optional variant-specific images
     * Example:
     * [
     *   { "url": "...", "altText": "Black Shirt Front" }
     * ]
     */
    private List<ProductImage> images = new ArrayList<>();
}

