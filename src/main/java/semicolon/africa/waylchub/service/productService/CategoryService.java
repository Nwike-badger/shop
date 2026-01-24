package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.dto.productDto.CategoryTreeResponse;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.repository.productRepository.CategoryRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryTreeResponse> getCategoryTree() {
        List<Category> roots = categoryRepository.findByParentIsNull();
        return roots.stream()
                .map(this::buildTree)
                .toList();
    }

    private CategoryTreeResponse buildTree(Category category) {
        CategoryTreeResponse dto = new CategoryTreeResponse();
        dto.setName(category.getName());
        dto.setSlug(category.getSlug());

        // 🔑 Explicit MongoDB lookup for children
        List<Category> children =
                categoryRepository.findByParentId(category.getId());

        dto.setChildren(
                children.stream()
                        .map(this::buildTree)
                        .toList()
        );

        return dto;
    }
}