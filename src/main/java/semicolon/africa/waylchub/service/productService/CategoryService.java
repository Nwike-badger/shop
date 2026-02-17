package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.productDto.CategoryRequest;
import semicolon.africa.waylchub.dto.productDto.CategoryTreeResponse;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.repository.productRepository.CategoryRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    // ========================================================================
    // 1. CREATE CATEGORY (Logic Restored + Cache Eviction Added)
    // ========================================================================

    @Transactional
    @CacheEvict(value = "categoryTree", allEntries = true)
    // ^^^ 🔑 KEY CHANGE: Wipes the cache so the new category appears immediately
    public Category createCategory(CategoryRequest req) {
        Category cat = new Category();
        cat.setName(req.getName());
        cat.setSlug(req.getSlug());
        cat.setDescription(req.getDescription());
        // cat.setImageUrl(req.getImageUrl()); // Assuming you add this to DTO

        // --- LINEAGE LOGIC (Ported from Old Service) ---
        if (req.getParentSlug() != null && !req.getParentSlug().isEmpty()) {
            Category parent = categoryRepository.findBySlug(req.getParentSlug())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found: " + req.getParentSlug()));

            cat.setParent(parent);

            // Logic: Parent's lineage + Parent's ID + ","
            String parentLineage = parent.getLineage() == null ? "," : parent.getLineage();
            cat.setLineage(parentLineage + parent.getId() + ",");
        } else {
            cat.setLineage(","); // Root Lineage
        }

        return categoryRepository.save(cat);
    }



    // i can optionally cache this too if the homepage load is heavy
    // @Cacheable(value = "featuredCategories")
    public List<Category> getFeaturedCategories() {
        // 1. Get all categories marked 'isFeatured' (Roots, Leaves, anything)
        List<Category> allFeatured = categoryRepository.findFeaturedCategoriesCustom();

        // 2. Bucket 1: The ones you specifically ordered (e.g., DisplayOrder 1, 2, 5)
        List<Category> ordered = allFeatured.stream()
                .filter(c -> c.getDisplayOrder() != null)
                .sorted(Comparator.comparingInt(Category::getDisplayOrder))
                .collect(Collectors.toList());

        // 3. Bucket 2: The ones you didn't number (Randoms)
        List<Category> randoms = allFeatured.stream()
                .filter(c -> c.getDisplayOrder() == null)
                .collect(Collectors.toList());

        // 4. Shuffle the random bucket
        Collections.shuffle(randoms);

        // 5. Merge: Ordered first, then Randoms
        ordered.addAll(randoms);

        return ordered;
    }

    // ========================================================================
    // 3. THE HIGH-PERFORMANCE TREE (Cached)
    // ========================================================================

    @Cacheable(value = "categoryTree", key = "'fullTree'")
    public List<CategoryTreeResponse> getCategoryTree() {

        List<Category> allCategories = categoryRepository.findAll();


        Map<String, List<Category>> childrenMap = allCategories.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));


        return allCategories.stream()
                .filter(c -> c.getParent() == null) // Roots only
                .map(root -> buildTreeInMemory(root, childrenMap))
                .toList();
    }

    // --- Helper for In-Memory Tree Building ---
    private CategoryTreeResponse buildTreeInMemory(Category category, Map<String, List<Category>> childrenMap) {
        CategoryTreeResponse dto = new CategoryTreeResponse();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setSlug(category.getSlug());

        // Get children from RAM map, NOT the database
        List<Category> children = childrenMap.getOrDefault(category.getId(), Collections.emptyList());

        dto.setChildren(children.stream()
                .map(child -> buildTreeInMemory(child, childrenMap))
                .toList());

        return dto;
    }
}