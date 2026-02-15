package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;
import org.springframework.data.support.PageableExecutionUtils; // ✅ CORRECT IMPORT

import semicolon.africa.waylchub.dto.productDto.ProductFilterRequest;
import semicolon.africa.waylchub.dto.productDto.ProductRequest;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.repository.productRepository.CategoryRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductServiceOld {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final MongoTemplate mongoTemplate;

//    public Product addOrUpdateProduct(ProductRequest request) {
//        Optional<Product> existingOpt = productRepository.findBySlug(request.getSlug());
//        Product product = existingOpt.orElse(new Product());
//
//        product.setName(request.getName());
//        product.setSlug(request.getSlug());
//        product.setSku(request.getSku());
//        product.setPrice(request.getPrice());
//
//
//        int currentStock = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
//        int newStock = request.getStockQuantity() == null ? 0 : request.getStockQuantity();
//        product.setStockQuantity(existingOpt.isPresent() ? currentStock + newStock : newStock);
//
//        if (request.getAttributes() != null) {
//            request.getAttributes().forEach(attr ->
//                    product.getAttributes().put(attr.getName(), attr.getValue())
//            );
//        }
//        if (request.getImages() != null) {
//            product.setImages(request.getImages());
//        }
//
//        if (request.getCategorySlug() != null) {
//            Category cat = categoryRepository.findBySlug(request.getCategorySlug())
//                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
//            product.setCategory(cat);
//            product.setCategoryName(cat.getName());
//            product.setCategorySlug(cat.getSlug());
//        }
//
//        return productRepository.save(product);
//    }
//
//    public Page<Product> filterProducts(ProductFilterRequest filter, Pageable pageable) {
//        Query query = new Query();
//
//        if (filter.getKeyword() != null && !filter.getKeyword().isEmpty()) {
//            query.addCriteria(TextCriteria.forDefaultLanguage().matching(filter.getKeyword()));
//        }
//
//        if (filter.getCategorySlug() != null) {
//            Category root = categoryRepository.findBySlug(filter.getCategorySlug())
//                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
//
//            List<Category> subCategories = categoryRepository.findAllByLineageContaining(root.getId());
//            subCategories.add(root);
//
//            query.addCriteria(Criteria.where("category").in(subCategories));
//        }
//
//        if (filter.getMinPrice() != null || filter.getMaxPrice() != null) {
//            Criteria price = Criteria.where("price");
//            if (filter.getMinPrice() != null) price.gte(filter.getMinPrice());
//            if (filter.getMaxPrice() != null) price.lte(filter.getMaxPrice());
//            query.addCriteria(price);
//        }
//
//        if (filter.getAttributes() != null) {
//            filter.getAttributes().forEach((key, value) ->
//                    query.addCriteria(Criteria.where("attributes." + key).is(value))
//            );
//        }
//
//        long count = mongoTemplate.count(query, Product.class);
//        List<Product> products = mongoTemplate.find(query.with(pageable), Product.class);
//
//
//        return PageableExecutionUtils.getPage(products, pageable, () -> count);
//    }
//
//    public List<Product> getAllProducts() {
//        return productRepository.findAll();
//    }
//
//    public void applyDiscount(String productId, BigDecimal newPrice) {
//        Product product = productRepository.findById(productId)
//                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
//
//        // Save the old price as "CompareAtPrice" so users see the discount
//        if (product.getCompareAtPrice() == null) {
//            product.setCompareAtPrice(product.getPrice());
//        }
//
//        product.setPrice(newPrice);
//        product.setOnSale(true);
//
//        productRepository.save(product);
//    }
//
//
////    public void incrementSalesCount(String productId, int quantitySold) {
////        // MongoDB "inc" operation is atomic and very fast
////        Update update = new Update().inc("soldCount", quantitySold);
////        Query query = new Query(Criteria.where("id").is(productId));
////        mongoTemplate.updateFirst(query, update, Product.class);
////    }
//
//    // Inside ReviewService
//    public void addReview(String productId, int starRating, String comment) {
//        // 1. Save the review in 'reviews' collection...
//
//        // 2. Recalculate average for the product
//        // (This keeps reads FAST because we don't calculate averages on every page load)
//        Product p = productRepository.findById(productId).get();
//
//        double newTotal = (p.getAverageRating() * p.getReviewCount()) + starRating;
//        int newCount = p.getReviewCount() + 1;
//
//        p.setReviewCount(newCount);
//        p.setAverageRating(newTotal / newCount);
//
//        productRepository.save(p);
//    }
//
//    public List<Product> getProductsByCategorySlug(String slug) {
//        // 1. Find the root category (e.g., "Mobile Phones")
//        Category root = categoryRepository.findBySlug(slug)
//                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
//
//        // 2. Collect ALL categories in this branch (Root + Children + Grandchildren)
//        List<Category> categoriesToSearch = new ArrayList<>();
//        collectCategoriesRecursively(root, categoriesToSearch);
//
//
//        return productRepository.findByCategoryIn(categoriesToSearch);
//    }
//
//
//    private void collectCategoriesRecursively(Category current, List<Category> accumulator) {
//        accumulator.add(current);
//        // Find children of the current category
//        List<Category> children = categoryRepository.findByParentId(current.getId());
//        for (Category child : children) {
//            collectCategoriesRecursively(child, accumulator);
//        }
//    }
//
//    public List<Product> searchProducts(String q) {
//        return productRepository.findByNameContainingIgnoreCase(q);
//    }
}