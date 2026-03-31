package semicolon.africa.waylchub.repository.recommendationRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import semicolon.africa.waylchub.model.recommendation.ProductPopularity;

import java.util.List;

public interface ProductPopularityRepository extends MongoRepository<ProductPopularity, String> {

    /** Top trending products in a specific category. */
    List<ProductPopularity> findByCategoryIdOrderByPopularityScoreDesc(String categoryId, Pageable pageable);

    /** Top trending products for a specific brand. */
    List<ProductPopularity> findByBrandIdOrderByPopularityScoreDesc(String brandId, Pageable pageable);

    /** Globally trending (used for homepage, fallback recommendations). */
    List<ProductPopularity> findAllByOrderByPopularityScoreDesc(Pageable pageable);
}