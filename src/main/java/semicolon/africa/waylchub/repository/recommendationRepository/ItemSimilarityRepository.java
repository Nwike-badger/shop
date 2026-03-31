package semicolon.africa.waylchub.repository.recommendationRepository;

import org.springframework.data.mongodb.repository.MongoRepository;
import semicolon.africa.waylchub.model.recommendation.ItemSimilarity;
import semicolon.africa.waylchub.model.recommendation.ItemSimilarity.SimilarityType;
import semicolon.africa.waylchub.model.recommendation.ProductPopularity;

import java.util.List;
import java.util.Optional;

public interface ItemSimilarityRepository extends MongoRepository<ItemSimilarity, String> {

    Optional<ItemSimilarity> findBySourceProductIdAndType(String sourceProductId, SimilarityType type);

    List<ItemSimilarity> findBySourceProductIdIn(List<String> productIds);

    void deleteBySourceProductId(String sourceProductId);
}