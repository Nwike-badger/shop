package semicolon.africa.waylchub.repository.productRepository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.model.product.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String> {

    // 1. Search by exact Category (e.g., just "Blenders")
    List<Product> findByCategory(Category category);

    // 2. THE MAGICAL QUERY: Search by a LIST of Categories
    // This allows fetching products from "Appliances" OR "Blenders" OR "Toasters" simultaneously
    List<Product> findByCategoryIn(List<Category> categories);

    // 3. Search by Name (Case insensitive)
    List<Product> findByNameContainingIgnoreCase(String name);

    // 4. Filter by Brand
    List<Product> findByBrandId(String brandId);

    Optional<Product> findBySku(String sku);

    // OR find by Slug if you don't use SKU yet
    Optional<Product> findBySlug(String slug);
}