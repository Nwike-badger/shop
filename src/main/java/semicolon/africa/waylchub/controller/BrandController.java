package semicolon.africa.waylchub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.productDto.BrandRequest;
import semicolon.africa.waylchub.model.product.Brand;
import semicolon.africa.waylchub.service.productService.BrandService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    /* ══════════════════════════════════════════════════════════
       PUBLIC
    ══════════════════════════════════════════════════════════ */

    /**
     * All brands — used by storefront filters and OrganizationCard dropdown.
     * GET /api/brands
     */
    @GetMapping
    public ResponseEntity<List<Brand>> getAllBrands() {
        return ResponseEntity.ok(brandService.getAllBrands());
    }

    /**
     * Single brand by slug — used by storefront brand pages.
     * GET /api/brands/{slug}
     */
    @GetMapping("/{slug}")
    public ResponseEntity<Brand> getBrand(@PathVariable String slug) {
        return ResponseEntity.ok(brandService.getBrand(slug));
    }

    /**
     * Product count for a brand — used by BrandManager stats.
     * GET /api/brands/{slug}/product-count
     */
    @GetMapping("/{slug}/product-count")
    public ResponseEntity<Map<String, Long>> getProductCount(@PathVariable String slug) {
        return ResponseEntity.ok(Map.of("count", brandService.countProductsForBrand(slug)));
    }

    /* ══════════════════════════════════════════════════════════
       ADMIN
    ══════════════════════════════════════════════════════════ */

    /**
     * Create a brand.
     * POST /api/brands
     * Body: { name, slug, description?, logoUrl?, website? }
     */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Brand> createBrand(@Valid @RequestBody BrandRequest request) {
        return new ResponseEntity<>(brandService.createBrand(request), HttpStatus.CREATED);
    }

    /**
     * Update a brand's metadata (slug is immutable — products reference it).
     * PUT /api/brands/{slug}
     */
    @PutMapping("/{slug}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Brand> updateBrand(
            @PathVariable String slug,
            @Valid @RequestBody BrandRequest request) {
        return ResponseEntity.ok(brandService.updateBrand(slug, request));
    }

    /**
     * Delete a brand.
     * DELETE /api/brands/{slug}
     *
     * Returns 409 Conflict if the brand still has products assigned.
     */
    @DeleteMapping("/{slug}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")

    public ResponseEntity<Map<String, String>> deleteBrand(@PathVariable String slug) {
        brandService.deleteBrand(slug);
        return ResponseEntity.ok(Map.of("message", "Brand '" + slug + "' deleted successfully."));
    }

    /* ── Local exception handlers (remove if using @ControllerAdvice) ── */

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