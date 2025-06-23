package semicolon.africa.waylchub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.service.productService.CategoryService;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<Category> addCategory(@RequestBody Category category) {
        return ResponseEntity.ok(categoryService.addCategory(category));
    }

    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    @GetMapping("/by-name/{name}")
    public ResponseEntity<Category> getByName(@PathVariable String name) {
        return ResponseEntity.ok(categoryService.getCategoryByName(name));
    }

    @GetMapping("/by-subcategory/{sub}")
    public ResponseEntity<List<Category>> getBySubCategory(@PathVariable String sub) {
        return ResponseEntity.ok(categoryService.getCategoriesBySubCategory(sub));
    }
}
