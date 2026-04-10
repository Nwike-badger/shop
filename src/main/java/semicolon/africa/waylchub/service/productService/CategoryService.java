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

/**
 * CategoryService — manages the full product category taxonomy.
 *
 * Fixes applied vs original:
 *  1. createCategory now persists imageUrl from the request.
 *  2. updateCategory now persists imageUrl from the request.
 *  3. updateCategory correctly promotes a child to root when
 *     parentSlug is explicitly passed as an empty string.
 *  4. deleteCategory provides a clean, descriptive error so the
 *     frontend can surface exactly why deletion was blocked.
 *
 * Ensure CategoryRequest has: name, slug, parentSlug, description, imageUrl.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /* ───────────────────────────────────────────────────────────
       CREATE
    ─────────────────────────────────────────────────────────── */

    @Transactional
    @CacheEvict(value = {"categoryTree", "featuredCategories"}, allEntries = true)
    public Category createCategory(CategoryRequest req) {
        // Prevent duplicate slugs
        if (categoryRepository.findBySlug(req.getSlug()).isPresent()) {
            throw new IllegalArgumentException(
                    "A category with slug '" + req.getSlug() + "' already exists.");
        }

        Category cat = new Category();
        cat.setName(req.getName());
        cat.setSlug(req.getSlug());
        cat.setDescription(req.getDescription());
        cat.setImageUrl(req.getImageUrl()); // ✅ FIX: was missing in original

        if (req.getParentSlug() != null && !req.getParentSlug().isBlank()) {
            Category parent = categoryRepository.findBySlug(req.getParentSlug())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent category not found: " + req.getParentSlug()));

            cat.setParent(parent);

            // Lineage: parent lineage + parent ID + ","  (e.g. ",1,5,")
            String parentLineage = (parent.getLineage() == null || parent.getLineage().isBlank())
                    ? ","
                    : parent.getLineage();
            cat.setLineage(parentLineage + parent.getId() + ",");
        } else {
            // Root category
            cat.setParent(null);
            cat.setLineage(",");
        }

        return categoryRepository.save(cat);
    }

    /* ───────────────────────────────────────────────────────────
       UPDATE
    ─────────────────────────────────────────────────────────── */

    @Transactional
    @CacheEvict(value = {"categoryTree", "featuredCategories"}, allEntries = true)
    public Category updateCategory(String slug, CategoryRequest req) {
        Category cat = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + slug));

        cat.setName(req.getName());
        cat.setDescription(req.getDescription());
        cat.setImageUrl(req.getImageUrl());

        // ── Reparent logic ────────────────────────────────────────
        // null parentSlug  → leave parent relationship unchanged
        // ""   parentSlug  → promote to root (remove parent)
        // "x"  parentSlug  → set parent to category with slug "x"
        if (req.getParentSlug() != null) {
            if (req.getParentSlug().isBlank()) {
                // Explicit empty string → make root
                cat.setParent(null);
                cat.setLineage(",");
            } else {
                // Prevent circular parenting: new parent must not be a descendant
                String targetParentSlug = req.getParentSlug().trim();
                if (targetParentSlug.equals(slug)) {
                    throw new IllegalArgumentException(
                            "A category cannot be its own parent.");
                }

                Category parent = categoryRepository.findBySlug(targetParentSlug)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Parent category not found: " + targetParentSlug));

                // Guard against circular reference via lineage
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
        // if req.getParentSlug() == null → leave parent as-is (no change)

        return categoryRepository.save(cat);
    }

    /* ───────────────────────────────────────────────────────────
       DELETE
    ─────────────────────────────────────────────────────────── */

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

        // Optional: warn if products are still assigned to this category.
        // Uncomment if you have a product repository check:
        // boolean hasProducts = productRepository.existsByCategorySlug(slug);
        // if (hasProducts) throw new IllegalStateException("Category has products attached.");

        categoryRepository.delete(cat);
    }

    /* ───────────────────────────────────────────────────────────
       READ — single category
    ─────────────────────────────────────────────────────────── */

    public Category getCategory(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + slug));
    }

    /* ───────────────────────────────────────────────────────────
       READ — featured
    ─────────────────────────────────────────────────────────── */

    @Cacheable(value = "featuredCategories")
    public List<Category> getFeaturedCategories() {
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
        return ordered;
    }

    /* ───────────────────────────────────────────────────────────
       READ — full tree (O(n) in-memory, no N+1 queries)
    ─────────────────────────────────────────────────────────── */

    @Cacheable(value = "categoryTree", key = "'fullTree'")
    public List<CategoryTreeResponse> getCategoryTree() {
        List<Category> all = categoryRepository.findAll();

        Map<String, List<Category>> childrenMap = all.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        return all.stream()
                .filter(c -> c.getParent() == null)
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
        dto.setDescription(category.getDescription()); // ✅ include description in tree

        List<Category> children = childrenMap.getOrDefault(
                category.getId(), Collections.emptyList());

        dto.setChildren(children.stream()
                .map(child -> buildTreeInMemory(child, childrenMap))
                .toList());

        return dto;
    }
}