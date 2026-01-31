package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.dto.productDto.CategoryRequest;
import semicolon.africa.waylchub.dto.productDto.CategoryTreeResponse;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.repository.productRepository.CategoryRepository;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public Category createCategory(CategoryRequest req) {
        Category cat = new Category();
        cat.setName(req.getName());
        cat.setSlug(req.getSlug());


        if (req.getParentSlug() != null && !req.getParentSlug().isEmpty()) {
            Category parent = categoryRepository.findBySlug(req.getParentSlug())
                    .orElseThrow(() -> new RuntimeException("Parent category not found: " + req.getParentSlug()));

            cat.setParent(parent);

            // ✅ Logic: Parent's lineage + Parent's ID + ","
            // If parent is root, its lineage might be ","
            String parentLineage = parent.getLineage() == null ? "," : parent.getLineage();
            cat.setLineage(parentLineage + parent.getId() + ",");
        } else {
            cat.setLineage(","); // Root Lineage
        }

        return categoryRepository.save(cat);
    }

    public List<CategoryTreeResponse> getCategoryTree() {
        List<Category> roots = categoryRepository.findByParentIsNull();
        return roots.stream().map(this::buildTree).toList();
    }

    public List<Category> getFeaturedCategories() {
        // 1. Get all categories marked to show on home (leaves, roots, whatever you picked)
        List<Category> allFeatured = categoryRepository.findFeaturedCategoriesCustom();

        // 2. Bucket 1: The ones you specifically ordered (e.g., 1, 2, 5)
        List<Category> ordered = allFeatured.stream()
                .filter(c -> c.getDisplayOrder() != null)
                .sorted(Comparator.comparingInt(Category::getDisplayOrder))
                .collect(Collectors.toList());

        // 3. Bucket 2: The ones you didn't number (Randoms)
        List<Category> randoms = allFeatured.stream()
                .filter(c -> c.getDisplayOrder() == null)
                .collect(Collectors.toList());

        // 4. Shuffle the random bucket to satisfy "arranges by random"
        Collections.shuffle(randoms);

        // 5. Merge: Ordered first, then Randoms
        ordered.addAll(randoms);

        return ordered;
    }



    private CategoryTreeResponse buildTree(Category category) {
        CategoryTreeResponse dto = new CategoryTreeResponse();
        dto.setName(category.getName());
        dto.setSlug(category.getSlug());

        // Recursive finding of children
        List<Category> children = categoryRepository.findByParentId(category.getId());
        dto.setChildren(children.stream().map(this::buildTree).toList());

        return dto;
    }
}