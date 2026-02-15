package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.dto.productDto.CategoryTreeResponse;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.repository.productRepository.CategoryRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    // --- READ (Cached) ---

    @Cacheable(value = "categoryTree", key = "'fullTree'")
    // ^^^ This saves the result to Redis. Next time, it skips the DB entirely.
    public List<CategoryTreeResponse> getCategoryTree() {
        List<Category> allCategories = categoryRepository.findAll();
        Map<String, List<Category>> childrenMap = allCategories.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        return allCategories.stream()
                .filter(c -> c.getParent() == null)
                .map(root -> buildTreeInMemory(root, childrenMap))
                .toList();
    }

    // --- WRITE (Evict Cache) ---

    @CacheEvict(value = "categoryTree", allEntries = true)
    // ^^^ This wipes the cache whenever you change categories, forcing a rebuild next time.
    public Category createCategory(Category category) {
        // ... your creation logic ...
        return categoryRepository.save(category);
    }

    @CacheEvict(value = "categoryTree", allEntries = true)
    public Category updateCategory(Category category) {
        // ... your update logic ...
        return categoryRepository.save(category);
    }

    private CategoryTreeResponse buildTreeInMemory(Category category, Map<String, List<Category>> childrenMap) {
        CategoryTreeResponse dto = new CategoryTreeResponse();
        dto.setName(category.getName());
        dto.setSlug(category.getSlug());
        dto.setId(category.getId()); // Helpful for frontend keys

        List<Category> children = childrenMap.getOrDefault(category.getId(), Collections.emptyList());
        dto.setChildren(children.stream()
                .map(child -> buildTreeInMemory(child, childrenMap))
                .toList());
        return dto;
    }
}