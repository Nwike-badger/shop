package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.repository.productRepository.CategoryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public Category addCategory(Category category) {
        return categoryRepository.save(category);
    }

    @Override
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Override
    public Category getCategoryByName(String name) {
        return categoryRepository.findByName(name).orElse(null);
    }

    @Override
    public List<Category> getCategoriesBySubCategory(String subCategory) {
        return categoryRepository.findBySubCategoriesContaining(subCategory);
    }
}
