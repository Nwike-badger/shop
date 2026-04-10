package semicolon.africa.waylchub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.productDto.CategoryRequest;
import semicolon.africa.waylchub.dto.productDto.CategoryTreeResponse;
import semicolon.africa.waylchub.model.product.Category;
import semicolon.africa.waylchub.service.productService.CategoryService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /* ══════════════════════════════════════════════════════════
       PUBLIC — no auth required (used by storefront)
    ══════════════════════════════════════════════════════════ */

    /**
     * Full category tree — used by storefront Navbar and CategoryManager.
     * GET /api/categories/tree
     */
    @GetMapping("/tree")
    public ResponseEntity<List<CategoryTreeResponse>> getCategoryTree() {
        return ResponseEntity.ok(categoryService.getCategoryTree());
    }

    /**
     * Featured categories — used by storefront home page CategoryBar.
     * GET /api/categories/featured
     */
    @GetMapping("/featured")
    public ResponseEntity<List<Category>> getFeaturedCategories() {
        return ResponseEntity.ok(categoryService.getFeaturedCategories());
    }

    /**
     * Single category by slug — used when editing a specific category.
     * GET /api/categories/{slug}
     */
    @GetMapping("/{slug}")
    public ResponseEntity<Category> getCategory(@PathVariable String slug) {
        return ResponseEntity.ok(categoryService.getCategory(slug));
    }

    /* ══════════════════════════════════════════════════════════
       ADMIN — ROLE_ADMIN required
    ══════════════════════════════════════════════════════════ */

    /**
     * Create a new category (root or child).
     * POST /api/categories
     *
     * Body: { name, slug, parentSlug?, description?, imageUrl? }
     */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Category> createCategory(
            @Valid @RequestBody CategoryRequest request) {
        return new ResponseEntity<>(
                categoryService.createCategory(request),
                HttpStatus.CREATED);
    }

    /**
     * Update an existing category — rename, reparent, change image, etc.
     * PUT /api/categories/{slug}
     *
     * Reparenting rules (parentSlug field):
     *   null        → leave parent relationship unchanged
     *   ""          → promote to root (remove parent)
     *   "some-slug" → nest under that parent
     */
    @PutMapping("/{slug}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Category> updateCategory(
            @PathVariable String slug,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(
                categoryService.updateCategory(slug, request));
    }

    /**
     * Delete a category.
     * DELETE /api/categories/{slug}
     *
     * Returns 409 Conflict if the category still has child categories.
     * The service layer throws IllegalStateException in that case,
     * which your global exception handler should map to 409.
     *
     * If you don't have a global handler yet, the fallback @ExceptionHandler
     * below catches it locally.
     */
    @DeleteMapping("/{slug}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Map<String, String>> deleteCategory(
            @PathVariable String slug) {
        categoryService.deleteCategory(slug);
        return ResponseEntity.ok(
                Map.of("message", "Category '" + slug + "' deleted successfully."));
    }

    /* ── Local exception handler (remove if you have a @ControllerAdvice) ── */

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }
}