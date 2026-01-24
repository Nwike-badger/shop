package semicolon.africa.waylchub.service.productService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.dto.productDto.ProductRequest;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.product.*;
import semicolon.africa.waylchub.repository.productRepository.CategoryRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
// You likely need a BrandRepository too
// import semicolon.africa.waylchub.repository.productRepository.BrandRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    // @Autowired private BrandRepository brandRepository; // Uncomment when you have this

    /**
     * CREATE OR UPDATE PRODUCT
     * Accepts DTO, converts to Entity, handles Relationships
     */
    public Product addOrUpdateProduct(ProductRequest request) {
        // 1. Check if product exists by Slug
        Optional<Product> existingProductOpt = productRepository.findBySlug(request.getSlug());

        if (existingProductOpt.isPresent()) {
            // --- SCENARIO A: UPDATE (Restock + Price Update) ---
            Product existingProduct = existingProductOpt.get();

            // Update Stock
            int currentStock = existingProduct.getStockQuantity() == null ? 0 : existingProduct.getStockQuantity();
            int addedStock = request.getStockQuantity() == null ? 0 : request.getStockQuantity();
            existingProduct.setStockQuantity(currentStock + addedStock);

            // Update Price if changed
            if (request.getPrice() != null) {
                existingProduct.setPrice(request.getPrice());
            }

            // Update Name/Desc if needed
            existingProduct.setName(request.getName());

            return productRepository.save(existingProduct);

        } else {
            // --- SCENARIO B: CREATE NEW ---
            Product newProduct = new Product();
            newProduct.setName(request.getName());
            newProduct.setSlug(request.getSlug());
            newProduct.setPrice(request.getPrice());
            newProduct.setStockQuantity(request.getStockQuantity() == null ? 0 : request.getStockQuantity());

            // MAP ATTRIBUTES
            if(request.getAttributes() != null) {
                List<ProductAttribute> attrs = request.getAttributes().stream()
                        .map(dto -> new ProductAttribute(dto.getName(), dto.getValue())) // Assuming ProductAttribute has this constructor
                        .collect(Collectors.toList());
                newProduct.setAttributes(attrs);
            }

            // LINK CATEGORY (Crucial Step)
            if (request.getCategorySlug() != null) {
                Category cat = categoryRepository.findBySlug(request.getCategorySlug())
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.getCategorySlug()));
                newProduct.setCategory(cat);
            }

            // LINK BRAND (Logic similar to Category)
            /*
            if (request.getBrandSlug() != null) {
                Brand brand = brandRepository.findBySlug(request.getBrandSlug())
                        .orElseThrow(() -> new RuntimeException("Brand not found"));
                newProduct.setBrand(brand);
            }
            */

            return productRepository.save(newProduct);
        }
    }

    public List<Product> getProductsByCategorySlug(String slug) {
        Category rootCategory = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + slug));

        List<Category> allCategoriesToSearch = new ArrayList<>();
        collectAllCategoryChildren(rootCategory, allCategoriesToSearch);

        return productRepository.findByCategoryIn(allCategoriesToSearch);
    }

    private void collectAllCategoryChildren(Category currentCategory, List<Category> accumulator) {
        accumulator.add(currentCategory);
        List<Category> children = categoryRepository.findByParentId(currentCategory.getId());
        for (Category child : children) {
            collectAllCategoryChildren(child, accumulator);
        }
    }

    public List<Product> searchProducts(String keyword) {
        return productRepository.findByNameContainingIgnoreCase(keyword);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
}