package semicolon.africa.waylchub.repository.productRepository;

import org.springframework.data.mongodb.repository.MongoRepository;
import semicolon.africa.waylchub.model.product.SiteConfig;

public interface SiteConfigRepository extends MongoRepository<SiteConfig, String> {}
