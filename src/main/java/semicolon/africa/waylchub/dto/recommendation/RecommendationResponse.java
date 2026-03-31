package semicolon.africa.waylchub.dto.recommendation;

import lombok.*;
import semicolon.africa.waylchub.model.product.Product;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {

    /** "Similar Products" — content-based, always populated */
    private List<Product> similarProducts;

    /** "Customers Also Bought" — collaborative filtering (CO_PURCHASE) */
    private List<Product> customersAlsoBought;

    /** "Customers Also Viewed" — collaborative filtering (CO_VIEW) */
    private List<Product> customersAlsoViewed;

    public static RecommendationResponse empty() {
        return RecommendationResponse.builder()
                .similarProducts(List.of())
                .customersAlsoBought(List.of())
                .customersAlsoViewed(List.of())
                .build();
    }
}