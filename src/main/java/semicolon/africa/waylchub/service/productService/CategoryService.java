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
    // WRITES — evict both caches on any structural change
    // =========================================================================

    @Transactional
    @CacheEvict(value = {"categoryTree", "featuredCategories"}, allEntries = true)
    public Category createCategory(CategoryRequest req) {

        if (categoryRepository.findBySlug(req.getSlug()).isPresent()) {
            throw new IllegalArgumentException(
                    "A category with slug '" + req.getSlug() + "' already exists.");
        }

        Category cat = new Category();
        cat.setName(req.getName());
        cat.setSlug(req.getSlug());
        cat.setDescription(req.getDescription());
        cat.setImageUrl(req.getImageUrl());

        if (req.getParentSlug() != null && !req.getParentSlug().isBlank()) {
            Category parent = categoryRepository.findBySlug(req.getParentSlug())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent category not found: " + req.getParentSlug()));

            cat.setParent(parent);

            String parentLineage = (parent.getLineage() == null || parent.getLineage().isBlank())
                    ? ","
                    : parent.getLineage();
            cat.setLineage(parentLineage + parent.getId() + ",");
        } else {
            cat.setParent(null);
            cat.setLineage(",");
        }

        return categoryRepository.save(cat);
    }

    @Transactional
    @CacheEvict(value = {"categoryTree", "featuredCategories"}, allEntries = true)
    public Category updateCategory(String slug, CategoryRequest req) {
        Category cat = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + slug));

        cat.setName(req.getName());
        cat.setDescription(req.getDescription());
        cat.setImageUrl(req.getImageUrl());

        if (req.getParentSlug() != null) {
            if (req.getParentSlug().isBlank()) {
                cat.setParent(null);
                cat.setLineage(",");
            } else {
                String targetParentSlug = req.getParentSlug().trim();
                if (targetParentSlug.equals(slug)) {
                    throw new IllegalArgumentException(
                            "A category cannot be its own parent.");
                }

                Category parent = categoryRepository.findBySlug(targetParentSlug)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Parent category not found: " + targetParentSlug));

                if (parent.getLineage() != null &&
                        parent.getLineage().contains("," + cat.getId() + ",")) {
                    throw new IllegalArgumentException(
                            "Circular category reference detected. '" +
                                    parent.getName() + "' is a descendant of '" + cat.getName() + "'.");
                }

                cat.setParent(parent);
                String parentLineage = (parent.getLineage() == null || parent.getLineage().isBlank())
                        ? ","
                        : parent.getLineage();
                cat.setLineage(parentLineage + parent.getId() + ",");
            }
        }

        return categoryRepository.save(cat);
    }

    @Transactional
    @CacheEvict(value = {"categoryTree", "featuredCategories"}, allEntries = true)
    public void deleteCategory(String slug) {
        Category cat = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + slug));

        boolean hasChildren = categoryRepository.existsByParentId(cat.getId());
        if (hasChildren) {
            throw new IllegalStateException(
                    "Cannot delete category '" + cat.getName() + "' — it has child categories. " +
                            "Delete or reassign the children first.");
        }

        categoryRepository.delete(cat);
    }

    // =========================================================================
    // READS
    // =========================================================================

    /**
     * Single category by slug — returns raw entity (not cached, not serialized to Redis).
     * Used only by the admin edit form; the heavy public reads use the cached methods below.
     */
    public Category getCategory(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + slug));
    }

    /**
     * Featured categories for the storefront home page.
     *
     * Returns List<CategoryTreeResponse> (not List<Category>) so that Redis
     * serializes a plain DTO with no @DBRef proxies or parent back-references.
     * Returning raw Category entities caused a 400 because Jackson's NON_FINAL
     * default typing attempted to traverse parent → parent.parent → …
     */
    @Cacheable(value = "featuredCategories")
    public List<CategoryTreeResponse> getFeaturedCategories() {
        List<Category> allFeatured = categoryRepository.findFeaturedCategoriesCustom();

        List<Category> ordered = allFeatured.stream()
                .filter(c -> c.getDisplayOrder() != null)
                .sorted(Comparator.comparingInt(Category::getDisplayOrder))
                .collect(Collectors.toList());

        List<Category> randoms = allFeatured.stream()
                .filter(c -> c.getDisplayOrder() == null)
                .collect(Collectors.toList());
        Collections.shuffle(randoms);

        ordered.addAll(randoms);

        // Map to DTO — children left empty (flat list is fine for the CategoryBar)
        return ordered.stream()
                .map(this::toCategoryDto)
                .collect(Collectors.toList());
    }

    /**
     * Full category tree for the Navbar and CategoryManager.
     * Already returns List<CategoryTreeResponse> — no change needed here.
     */
    @Cacheable(value = "categoryTree", key = "'fullTree'")
    public List<CategoryTreeResponse> getCategoryTree() {
        List<Category> all = categoryRepository.findAll();

        // Group children by parent id
        Map<String, List<Category>> childrenMap = all.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        return all.stream()
                .filter(c -> c.getParent() == null)
                .map(root -> buildTreeInMemory(root, childrenMap))
                .toList();
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private CategoryTreeResponse buildTreeInMemory(
            Category category,
            Map<String, List<Category>> childrenMap) {

        CategoryTreeResponse dto = toCategoryDto(category);

        List<Category> children = childrenMap.getOrDefault(
                category.getId(), Collections.emptyList());

        dto.setChildren(children.stream()
                .map(child -> buildTreeInMemory(child, childrenMap))
                .toList());

        return dto;
    }

    /**
     * Maps a Category entity to a Redis-safe DTO.
     * No @DBRef fields, no parent reference, no MongoDB internals.
     */
    private CategoryTreeResponse toCategoryDto(Category c) {
        CategoryTreeResponse dto = new CategoryTreeResponse();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setSlug(c.getSlug());
        dto.setDescription(c.getDescription());
        dto.setImageUrl(c.getImageUrl());
        dto.setFeatured(c.isFeatured());
        dto.setDisplayOrder(c.getDisplayOrder());
        dto.setActive(c.isActive());
        // children defaults to empty list via field initializer — safe for Redis
        return dto;
    }
}