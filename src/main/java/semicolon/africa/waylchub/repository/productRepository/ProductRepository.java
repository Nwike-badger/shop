package semicolon.africa.waylchub.repository.productRepository;

import org.springframework.data.mongodb.repository.MongoRepository;
import semicolon.africa.waylchub.model.product.Product;

import java.util.List;

public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findByCategory(String category);
    List<Product> findBySubCategory(String subCategory);
//    List<Product> findByIsNewArrivalTrue();
//    List<Product> findByIsBestSellerTrue();
}
