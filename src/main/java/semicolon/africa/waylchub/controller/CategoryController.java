package semicolon.africa.waylchub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import semicolon.africa.waylchub.dto.productDto.CategoryTreeResponse;
import semicolon.africa.waylchub.service.productService.CategoryService;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/tree")
    public ResponseEntity<List<CategoryTreeResponse>> getTree() {
        return ResponseEntity.ok(categoryService.getCategoryTree());
    }
}
