package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.dto.productDto.CategoryRequest;
import semicolon.africa.waylchub.dto.productDto.CategoryTreeResponse;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.repository.productRepository.CategoryRepository;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

import static semicolon.africa.waylchub.config.CacheConfig.PRODUCTS_LIST_CACHE;
import static semicolon.africa.waylchub.config.CacheConfig.PRODUCT_DETAIL_CACHE;

/**
 * Category tree where the same NAME can appear under different parents:
 *
 *   Men   › Shirts  (men-shirts)      Women › Shirts  (women-shirts)
 *
 * Rules:
 *   - slug is unique everywhere (it's the URL)
 *   - name is unique only among siblings (no two "Shirts" directly under Men)
 *   - if no slug is sent on create, it's generated: "<parent-slug>-<name>"
 *
 * Whole-tree reads use CategoryFastLoader (one query) instead of
 * categoryRepository.findAll() (one extra query per category for its @DBRef parent).
 *
 * No @Transactional: each write is small and safe to repeat, and on a slow link
 * Atlas aborts transactions that run past 60 seconds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final Comparator<Category> ORDER =
            Comparator.comparing(Category::getDisplayOrder,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Category::getName,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    private final CategoryRepository categoryRepository;
    private final MongoTemplate mongoTemplate;
    private final ProductCategoryIndexer indexer;
    private final CategoryFastLoader fastLoader;

    // =========================================================================
    // WRITES
    // =========================================================================

    @CacheEvict(value = {"categoryTree", "featuredCategories"}, allEntries = true)
    public Category createCategory(CategoryRequest req) {
        String name = requireName(req.getName());

        Category parent = null;
        if (req.getParentSlug() != null && !req.getParentSlug().isBlank()) {
            parent = categoryRepository.findBySlug(req.getParentSlug().trim())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent category not found: " + req.getParentSlug()));
        }

        assertNoSiblingWithName(parent == null ? null : parent.getId(),
                parent == null ? null : parent.getName(), name, null);

        String slug = (req.getSlug() == null || req.getSlug().isBlank())
                ? slugify(parent == null ? name : parent.getSlug() + "-" + name)
                : slugify(req.getSlug());
        if (categoryRepository.findBySlug(slug).isPresent()) {
            throw new IllegalArgumentException(
                    "A category with slug '" + slug + "' already exists. Choose a different slug.");
        }

        Category cat = new Category();
        cat.setName(name);
        cat.setSlug(slug);
        cat.setDescription(req.getDescription());
        cat.setImageUrl(req.getImageUrl());
        cat.setParent(parent);
        cat.setLineage(childLineage(parent == null ? null : parent.getId(),
                parent == null ? null : parent.getLineage()));
        return categoryRepository.save(cat);
    }

    /**
     * Rename and/or move a category. Afterwards every descendant's lineage is
     * rebuilt and every product inside this branch is re-indexed.
     *
     * parentSlug: null = keep parent, "" = make top-level, slug = move under it.
     */
    @CacheEvict(value = {"categoryTree", "featuredCategories", PRODUCTS_LIST_CACHE, PRODUCT_DETAIL_CACHE},
            allEntries = true)
    public Category updateCategory(String slug, CategoryRequest req) {
        Category cat = getCategory(slug);
        String name = requireName(req.getName());

        Category newParent = cat.getParent();
        if (req.getParentSlug() != null) {
            if (req.getParentSlug().isBlank()) {
                newParent = null;
            } else {
                String target = req.getParentSlug().trim();
                if (target.equals(slug)) {
                    throw new IllegalArgumentException("A category cannot be its own parent.");
                }
                Category p = categoryRepository.findBySlug(target)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Parent category not found: " + target));
                if (p.getLineage() != null && p.getLineage().contains("," + cat.getId() + ",")) {
                    throw new IllegalArgumentException("Circular category reference: '" +
                            p.getName() + "' is inside '" + cat.getName() + "'.");
                }
                newParent = p;
            }
        }

        assertNoSiblingWithName(newParent == null ? null : newParent.getId(),
                newParent == null ? null : newParent.getName(), name, cat.getId());

        cat.setName(name);
        cat.setDescription(req.getDescription());
        cat.setImageUrl(req.getImageUrl());
        cat.setParent(newParent);
        cat.setLineage(childLineage(newParent == null ? null : newParent.getId(),
                newParent == null ? null : newParent.getLineage()));
        Category saved = categoryRepository.save(cat);

        int moved = rebuildDescendantLineages(saved.getId(), saved.getLineage());
        int n = indexer.reindexProductsUnder(saved.getId());
        log.info("[Category] Updated '{}' — {} sub-category lineage(s) fixed, {} product(s) re-indexed",
                saved.getSlug(), moved, n);
        return saved;
    }

    /** Refused while it has sub-categories or any product is assigned to it. */
    @CacheEvict(value = {"categoryTree", "featuredCategories"}, allEntries = true)
    public void deleteCategory(String slug) {
        Category cat = getCategory(slug);

        if (categoryRepository.existsByParentId(cat.getId())) {
            throw new IllegalStateException("Cannot delete '" + cat.getName() +
                    "' — it has sub-categories. Delete or move them first.");
        }

        long productCount = mongoTemplate.count(new Query(new Criteria().orOperator(
                Criteria.where("categoryIds").is(cat.getId()),
                Criteria.where("categorySlug").is(slug))), Product.class);
        if (productCount > 0) {
            throw new IllegalStateException("Cannot delete '" + cat.getName() + "' — " +
                    productCount + " product" + (productCount > 1 ? "s are" : " is") +
                    " still in it. Remove it from those products first.");
        }

        categoryRepository.delete(cat);
    }

    // =========================================================================
    // READS
    // =========================================================================

    /** Single category by slug — admin edit form. Not cached. */
    public Category getCategory(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + slug));
    }

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

        return ordered.stream().map(this::toCategoryDto).collect(Collectors.toList());
    }

    /** Full tree for the navbar and admin. One query. Children sorted by displayOrder, then name. */
    @Cacheable(value = "categoryTree", key = "'fullTree'")
    public List<CategoryTreeResponse> getCategoryTree() {
        List<Category> all = fastLoader.findAll();
        Set<String> ids = all.stream().map(Category::getId).collect(Collectors.toSet());

        Map<String, List<Category>> childrenMap = all.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        // A category whose parent no longer exists is shown at the top level
        // rather than disappearing.
        return all.stream()
                .filter(c -> c.getParent() == null || !ids.contains(c.getParent().getId()))
                .sorted(ORDER)
                .map(root -> buildTreeInMemory(root, childrenMap))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static String requireName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required.");
        return name.trim();
    }

    /** "Shirts" may exist under Men and under Women, but not twice under Men. One query. */
    private void assertNoSiblingWithName(String parentId, String parentName, String name, String excludeId) {
        boolean clash = fastLoader.findAll().stream()
                .filter(c -> !Objects.equals(c.getId(), excludeId))
                .filter(c -> Objects.equals(c.getParent() == null ? null : c.getParent().getId(), parentId))
                .anyMatch(c -> name.equalsIgnoreCase(c.getName()));
        if (clash) {
            throw new IllegalArgumentException("'" + name + "' already exists " +
                    (parentId == null ? "at the top level." : "under '" + parentName + "'."));
        }
    }

    private static String childLineage(String parentId, String parentLineage) {
        if (parentId == null) return ",";
        String base = (parentLineage == null || parentLineage.isBlank()) ? "," : parentLineage;
        return base + parentId + ",";
    }

    /**
     * After a move, fix the lineage of every category below this one.
     * One read, then a small $set only for children whose lineage actually changed.
     * @return how many lineages were changed
     */
    private int rebuildDescendantLineages(String rootId, String rootLineage) {
        Map<String, List<Category>> childrenMap = fastLoader.findAll().stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        Map<String, String> lineageById = new HashMap<>();
        lineageById.put(rootId, rootLineage);

        Deque<String> queue = new ArrayDeque<>(List.of(rootId));
        Set<String> seen = new HashSet<>();
        int changed = 0;

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            if (!seen.add(currentId)) continue; // guard against bad data loops
            String currentLineage = lineageById.get(currentId);

            for (Category child : childrenMap.getOrDefault(currentId, List.of())) {
                String newLineage = childLineage(currentId, currentLineage);
                if (!newLineage.equals(child.getLineage())) {
                    mongoTemplate.updateFirst(
                            new Query(Criteria.where("id").is(child.getId())),
                            new Update().set("lineage", newLineage),
                            Category.class);
                    changed++;
                }
                lineageById.put(child.getId(), newLineage);
                queue.add(child.getId());
            }
        }
        return changed;
    }

    static String slugify(String input) {
        String s = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (s.isEmpty()) throw new IllegalArgumentException("Could not build a slug from '" + input + "'.");
        return s;
    }

    private CategoryTreeResponse buildTreeInMemory(Category category, Map<String, List<Category>> childrenMap) {
        CategoryTreeResponse dto = toCategoryDto(category);
        dto.setChildren(childrenMap.getOrDefault(category.getId(), Collections.emptyList()).stream()
                .sorted(ORDER)
                .map(child -> buildTreeInMemory(child, childrenMap))
                .collect(Collectors.toList()));
        return dto;
    }

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