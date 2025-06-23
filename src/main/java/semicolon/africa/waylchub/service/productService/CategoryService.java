package semicolon.africa.waylchub.service.productService;

import semicolon.africa.waylchub.model.product.Category;

import java.util.List;

public interface CategoryService {
    Category addCategory(Category category);
    List<Category> getAllCategories();
    Category getCategoryByName(String name);
    List<Category> getCategoriesBySubCategory(String subCategory);
}
