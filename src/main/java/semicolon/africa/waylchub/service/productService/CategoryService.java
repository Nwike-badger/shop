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

    // =========================================================================
    // 1. CREATE
    // =========================================================================

    @Transactional
    @CacheEvict(value = {"categoryTree", "featuredCategories"}, allEntries = true)
    public Category createCategory(CategoryRequest req) {
        Category cat = new Category();
        cat.setName(req.getName());
        cat.setSlug(req.getSlug());
        cat.setDescription(req.getDescription());

        if (req.getParentSlug() != null && !req.getParentSlug().isEmpty()) {
            Category parent = categoryRepository.findBySlug(req.getParentSlug())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent category not found: " + req.getParentSlug()));

            cat.setParent(parent);

            // Lineage: parent's lineage + parent's ID + ","
            String parentLineage = parent.getLineage() == null ? "," : parent.getLineage();
            cat.setLineage(parentLineage + parent.getId() + ",");
        } else {
            cat.setLineage(","); // Root node
        }

        return categoryRepository.save(cat);
    }

    // =========================================================================
    // 2. UPDATE
    // FIX: Missing @CacheEvict — without this, edits to a category name, slug,
    // or description were invisible to users for up to 24 hours.
    // =========================================================================

    @Transactional
    @CacheEvict(value = {"categoryTree", "featuredCategories"}, allEntries = true)
    public Category updateCategory(String slug, CategoryRequest req) {
        Category cat = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + slug));

        cat.setName(req.getName());
        cat.setDescription(req.getDescription());

        // Slug changes are intentionally not supported here — changing a slug
        // would break all existing product categorySlug references and URLs.
        // If you need slug changes, add a dedicated migration step.

        // Handle parent change — recalculate lineage if parent is being updated
        if (req.getParentSlug() != null && !req.getParentSlug().isEmpty()) {
            Category parent = categoryRepository.findBySlug(req.getParentSlug())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent category not found: " + req.getParentSlug()));

            cat.setParent(parent);
            String parentLineage = parent.getLineage() == null ? "," : parent.getLineage();
            cat.setLineage(parentLineage + parent.getId() + ",");
        } else if (req.getParentSlug() != null) {
            // Explicit empty string means "make this a root category"
            cat.setParent(null);
            cat.setLineage(",");
        }

        return categoryRepository.save(cat);
    }

    // =========================================================================
    // 3. DELETE
    // FIX: Missing @CacheEvict — without this, deleted categories continued
    // to appear in the navbar for up to 24 hours after deletion.
    // =========================================================================

    @Transactional
    @CacheEvict(value = {"categoryTree", "featuredCategories"}, allEntries = true)
    public void deleteCategory(String slug) {
        Category cat = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + slug));

        // Guard: prevent deletion of a category that still has children.
        // Deleting a parent with active children would orphan them and
        // corrupt the lineage tree.
        boolean hasChildren = categoryRepository.existsByParentId(cat.getId());
        if (hasChildren) {
            throw new IllegalStateException(
                    "Cannot delete category '" + slug + "' — it has child categories. " +
                            "Delete or reassign the children first.");
        }

        categoryRepository.delete(cat);
    }

    // =========================================================================
    // 4. FEATURED CATEGORIES
    // =========================================================================

    @Cacheable(value = "featuredCategories")
    public List<Category> getFeaturedCategories() {
        List<Category> allFeatured = categoryRepository.findFeaturedCategoriesCustom();

        // Deterministically-ordered items come first
        List<Category> ordered = allFeatured.stream()
                .filter(c -> c.getDisplayOrder() != null)
                .sorted(Comparator.comparingInt(Category::getDisplayOrder))
                .collect(Collectors.toList());

        // Unordered items are shuffled for variety
        List<Category> randoms = allFeatured.stream()
                .filter(c -> c.getDisplayOrder() == null)
                .collect(Collectors.toList());
        Collections.shuffle(randoms);

        ordered.addAll(randoms);
        return ordered;
    }

    // =========================================================================
    // 5. CATEGORY TREE (Cached — the heavy lifting for the navbar mega-menu)
    // =========================================================================

    @Cacheable(value = "categoryTree", key = "'fullTree'")
    public List<CategoryTreeResponse> getCategoryTree() {
        List<Category> allCategories = categoryRepository.findAll();

        // Build children map in-memory — O(n) instead of N+1 queries
        Map<String, List<Category>> childrenMap = allCategories.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        return allCategories.stream()
                .filter(c -> c.getParent() == null) // Root nodes only
                .map(root -> buildTreeInMemory(root, childrenMap))
                .toList();
    }

    private CategoryTreeResponse buildTreeInMemory(
            Category category,
            Map<String, List<Category>> childrenMap) {

        CategoryTreeResponse dto = new CategoryTreeResponse();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setSlug(category.getSlug());
        dto.setImageUrl(category.getImageUrl());

        List<Category> children = childrenMap.getOrDefault(
                category.getId(), Collections.emptyList());

        dto.setChildren(children.stream()
                .map(child -> buildTreeInMemory(child, childrenMap))
                .toList());

        return dto;
    }
}