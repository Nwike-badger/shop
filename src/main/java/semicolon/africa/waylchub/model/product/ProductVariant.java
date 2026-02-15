package semicolon.africa.waylchub.model.product;

import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.*;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "product_variants")
@CompoundIndexes({
        @CompoundIndex(name = "product_price_idx",
                def = "{'productId': 1, 'price': 1}"),
        @CompoundIndex(name = "product_stock_idx",
                def = "{'productId': 1, 'stockQuantity': 1}")
})
public class ProductVariant {

    @Id
    private String id;

    @Indexed
    private String productId;

    @Indexed(unique = true)
    private String sku;

    private BigDecimal price;
    private BigDecimal compareAtPrice;

    private Integer stockQuantity;
    private Integer lowStockThreshold = 5;

    private boolean manageStock = true;

    @Builder.Default
    private Map<String, String> attributes = new HashMap<>();

    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    private boolean isActive = true;

    @CreatedDate
    private Instant createdAt;


    @LastModifiedDate
    private Instant updatedAt;
}
