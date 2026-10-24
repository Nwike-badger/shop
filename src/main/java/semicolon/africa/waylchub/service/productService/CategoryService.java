package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.productDto.CategoryRequest;
import semicolon.africa.waylchub.dto.productDto.CategoryTreeResponse;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.repository.productRepository.CategoryRepository;

import java.util.*;
import java.util.stream.Collectors;

import static semicolon.africa.waylchub.config.CacheConfig.PRODUCTS_LIST_CACHE;
import static semicolon.africa.waylchub.config.CacheConfig.PRODUCT_DETAIL_CACHE;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final MongoTemplate mongoTemplate;

    // =========================================================================
    // WRITES — evict caches on any structural change
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

    /**
     * Updates a category. Renaming or moving it also updates:
     *   - every product in it (categoryName + categoryLineageIds), so the products
     *     keep showing on the right parent pages
     *   - every child category's lineage, recursively
     * Product caches are cleared too, because product cards carry the category name.
     *
     * parentSlug: null = leave the parent alone, "" = make top-level, slug = move under it.
     */
    @Transactional
    @CacheEvict(value = {"categoryTree", "featuredCategories", PRODUCTS_LIST_CACHE, PRODUCT_DETAIL_CACHE},
            allEntries = true)
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

        Category saved = categoryRepository.save(cat);
        propagate(saved, 0);
        return saved;
    }

    /**
     * Deletes a category. Refused if it still has child categories or products,
     * so nothing is ever left orphaned.
     */
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

        long productCount = mongoTemplate.count(
                new Query(Criteria.where("categorySlug").is(slug)), Product.class);
        if (productCount > 0) {
            throw new IllegalStateException(
                    "Cannot delete category '" + cat.getName() + "' — it still has " + productCount +
                            " product" + (productCount > 1 ? "s" : "") +
                            ". Move them to another category first.");
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
     * serialises a plain DTO with no @DBRef proxies or parent back-references.
     */
    @Cacheable(value = "featuredCategories")
    public List<CategoryTreeResponse> getFeaturedCategories() {
        List<Category> allFeatured = categoryRepository.findFeaturedCategoriesCustom();

        List<Category> ordered = allFeatured.stream()
                .filter(c -> c.getDisplayOrder() != null)
                .sorted(Comparator.comparingInt(Category::getDisplayOrder))
                .collect(Collectors.toList());   // mutable ArrayList — Jackson-safe

        List<Category> randoms = allFeatured.stream()
                .filter(c -> c.getDisplayOrder() == null)
                .collect(Collectors.toList());   // mutable ArrayList — Jackson-safe
        Collections.shuffle(randoms);

        ordered.addAll(randoms);

        return ordered.stream()
                .map(this::toCategoryDto)
                .collect(Collectors.toList());   // mutable ArrayList — Jackson-safe
    }

    /**
     * Full category tree for the Navbar and CategoryManager.
     *
     * Uses Collectors.toList() (plain ArrayList) instead of Stream.toList()
     * (ImmutableCollections$List12), because Jackson's WRAPPER_ARRAY default typing
     * in Redis can't round-trip the immutable list class.
     */
    @Cacheable(value = "categoryTree", key = "'fullTree'")
    public List<CategoryTreeResponse> getCategoryTree() {
        List<Category> all = categoryRepository.findAll();

        Map<String, List<Category>> childrenMap = all.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        return all.stream()
                .filter(c -> c.getParent() == null)
                .map(root -> buildTreeInMemory(root, childrenMap))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /** After a rename or move, update the category's products and its children. */
    private void propagate(Category cat, int depth) {
        if (depth > 10 || cat.getId() == null) return;

        mongoTemplate.updateMulti(
                new Query(Criteria.where("categorySlug").is(cat.getSlug())),
                new Update().set("categoryName", cat.getName())
                        .set("categoryLineageIds", lineageIds(cat)),
                Product.class);

        String base = (cat.getLineage() == null || cat.getLineage().isBlank()) ? "," : cat.getLineage();
        for (Category child : categoryRepository.findAll()) {
            if (child.getParent() != null && cat.getId().equals(child.getParent().getId())) {
                child.setLineage(base + cat.getId() + ",");
                categoryRepository.save(child);
                propagate(child, depth + 1);
            }
        }
    }

    /** Ancestor ids from the lineage string, plus the category's own id. */
    private List<String> lineageIds(Category cat) {
        List<String> ids = new ArrayList<>();
        if (cat.getLineage() != null) {
            for (String p : cat.getLineage().split(",")) {
                if (!p.isBlank()) ids.add(p.trim());
            }
        }
        if (cat.getId() != null) ids.add(cat.getId());
        return ids;
    }

    private CategoryTreeResponse buildTreeInMemory(
            Category category,
            Map<String, List<Category>> childrenMap) {

        CategoryTreeResponse dto = toCategoryDto(category);

        List<Category> children = childrenMap.getOrDefault(
                category.getId(), Collections.emptyList());

        dto.setChildren(children.stream()
                .map(child -> buildTreeInMemory(child, childrenMap))
                .collect(Collectors.toList()));

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
        return dto;
    }
}