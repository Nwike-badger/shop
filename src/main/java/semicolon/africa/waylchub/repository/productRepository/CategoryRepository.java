//package semicolon.africa.waylchub.repository.productRepository;
//
//import org.springframework.data.mongodb.repository.MongoRepository;
//
//import java.util.List;
//import java.util.Optional;
//
//public interface CategoryRepository extends MongoRepository<Category, String> {
//    Optional<Category> findByName(String name);
//    List<Category> findBySubCategoriesContaining(String subCategory);
//    boolean existsByName(String name);
//}
