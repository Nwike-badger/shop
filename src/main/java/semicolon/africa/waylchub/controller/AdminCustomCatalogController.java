package semicolon.africa.waylchub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.customOrderDto.CustomCategoryRequest;
import semicolon.africa.waylchub.dto.customOrderDto.CustomCategoryResponse;
import semicolon.africa.waylchub.dto.customOrderDto.CustomStyleRequest;
import semicolon.africa.waylchub.dto.customOrderDto.CustomStyleResponse;
import semicolon.africa.waylchub.model.customOrder.CustomCategory;
import semicolon.africa.waylchub.model.customOrder.CustomStyle;
import semicolon.africa.waylchub.service.customOrderService.CustomCategoryService;
import semicolon.africa.waylchub.service.customOrderService.CustomStyleService;

import java.util.List;

/**
 * Admin CRUD for custom categories and the styles inside them.
 *
 * All endpoints require ROLE_ADMIN. The path is under /admin to align with
 * the convention in your SecurityConfig (.requestMatchers("/api/v1/admin/**")
 * .hasRole("ADMIN")) — saves wiring per-endpoint @PreAuthorize, though I keep
 * the annotation on each method for defense in depth.
 *
 * Categories:
 *   GET    /admin/custom-catalog/categories                 — list ALL incl. inactive
 *   POST   /admin/custom-catalog/categories                 — create
 *   GET    /admin/custom-catalog/categories/{slug}          — read one (with order count)
 *   PUT    /admin/custom-catalog/categories/{slug}          — update
 *   DELETE /admin/custom-catalog/categories/{slug}          — delete (blocked if orders exist)
 *
 * Styles (nested under category):
 *   GET    /admin/custom-catalog/categories/{slug}/styles   — list all incl. inactive
 *   POST   /admin/custom-catalog/categories/{slug}/styles   — create
 *   PUT    /admin/custom-catalog/styles/{styleSlug}         — update
 *   DELETE /admin/custom-catalog/styles/{styleSlug}         — delete
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/custom-catalog")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCustomCatalogController {

    private final CustomCategoryService categoryService;
    private final CustomStyleService styleService;

    // ─── Categories ──────────────────────────────────────────────────────

    @GetMapping("/categories")
    public ResponseEntity<List<CustomCategoryResponse>> listAll() {
        List<CustomCategoryResponse> body = categoryService.listAll().stream()
                .map(c -> {
                    CustomCategoryResponse r = CustomCategoryResponse.from(c);
                    r.setOrderCount(categoryService.countOrdersForCategory(c.getSlug()));
                    return r;
                })
                .toList();
        return ResponseEntity.ok(body);
    }

    @PostMapping("/categories")
    public ResponseEntity<CustomCategoryResponse> create(@Valid @RequestBody CustomCategoryRequest request) {
        CustomCategory created = categoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomCategoryResponse.from(created));
    }

    /** Single-category read for the admin editor — includes order count. */
    @GetMapping("/categories/{slug}")
    public ResponseEntity<CustomCategoryResponse> getCategory(@PathVariable String slug) {
        CustomCategory category = categoryService.getBySlug(slug);

        // Admin gets ALL styles (active + inactive)
        List<CustomStyle> styles = styleService.listAllForCategory(slug);

        CustomCategoryResponse response = CustomCategoryResponse.from(category);
        response.setOrderCount(categoryService.countOrdersForCategory(slug));
        response.setSampleStyles(styles.stream().map(CustomStyleResponse::from).toList());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/categories/{slug}")
    public ResponseEntity<CustomCategoryResponse> update(@PathVariable String slug,
                                                         @Valid @RequestBody CustomCategoryRequest request) {
        CustomCategory updated = categoryService.update(slug, request);
        return ResponseEntity.ok(CustomCategoryResponse.from(updated));
    }

    @DeleteMapping("/categories/{slug}")
    public ResponseEntity<Void> delete(@PathVariable String slug) {
        categoryService.delete(slug);
        return ResponseEntity.noContent().build();
    }

    // ─── Styles ──────────────────────────────────────────────────────────

    @GetMapping("/categories/{categorySlug}/styles")
    public ResponseEntity<List<CustomStyleResponse>> listStyles(@PathVariable String categorySlug) {
        // Validate parent exists
        categoryService.getBySlug(categorySlug);
        List<CustomStyleResponse> body = styleService.listAllForCategory(categorySlug).stream()
                .map(CustomStyleResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    @PostMapping("/categories/{categorySlug}/styles")
    public ResponseEntity<CustomStyleResponse> createStyle(@PathVariable String categorySlug,
                                                           @Valid @RequestBody CustomStyleRequest request) {
        CustomStyle created = styleService.create(categorySlug, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomStyleResponse.from(created));
    }

    @PutMapping("/styles/{styleSlug}")
    public ResponseEntity<CustomStyleResponse> updateStyle(@PathVariable String styleSlug,
                                                           @Valid @RequestBody CustomStyleRequest request) {
        CustomStyle updated = styleService.update(styleSlug, request);
        return ResponseEntity.ok(CustomStyleResponse.from(updated));
    }

    @DeleteMapping("/styles/{styleSlug}")
    public ResponseEntity<Void> deleteStyle(@PathVariable String styleSlug) {
        styleService.delete(styleSlug);
        return ResponseEntity.noContent().build();
    }
}