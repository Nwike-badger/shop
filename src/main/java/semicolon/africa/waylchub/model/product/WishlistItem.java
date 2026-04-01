package semicolon.africa.waylchub.model.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItem {
    private String productId;
    private String productName;
    private String slug;
    private String categorySlug;
    private String brandName;
    private String imageUrl;

    // We store price snapshots here for quick UI rendering,
    // but the frontend should verify live price before checkout
    private BigDecimal currentPrice;
    private BigDecimal compareAtPrice;

    @Builder.Default
    private LocalDateTime addedAt = LocalDateTime.now();
}