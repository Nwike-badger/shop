package semicolon.africa.waylchub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.productDto.*;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.model.product.ProductVariant;
import semicolon.africa.waylchub.service.productService.ProductService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ── WRITE ENDPOINTS (Admin only) ──────────────────────────────────────────

    /**
     * ✅ FIX: Added @PreAuthorize — without this, any anonymous user could
     * create products on your storefront.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ProductResponse> addProduct(@Valid @RequestBody ProductRequest request) {
        Product savedProduct = productService.createOrUpdateProduct(request);
        return new ResponseEntity<>(mapToResponse(savedProduct), HttpStatus.CREATED);
    }

    /**
     * ✅ FIX: Added @PreAuthorize — variants are pricing data, must be admin-only.
     */
    @PostMapping("/variants")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ProductVariant> addVariant(@Valid @RequestBody VariantRequest request) {
        ProductVariant savedVariant = productService.saveVariant(request);
        return new ResponseEntity<>(savedVariant, HttpStatus.CREATED);
    }

    @PostMapping("/{id}/reviews")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')") // Must be logged in to review
    public ResponseEntity<Void> addReview(@PathVariable String id, @RequestParam int rating) {
        productService.addReview(id, rating);
        return ResponseEntity.ok().build();
    }

    /**
     * ✅ NEW: Delete an entire product and all its variants
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable String id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * ✅ NEW: Delete a specific variant
     */
    @DeleteMapping("/variants/{variantId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteVariant(@PathVariable String variantId) {
        productService.deleteVariant(variantId);
        return ResponseEntity.noContent().build();
    }
    // ── READ ENDPOINTS (Public) ───────────────────────────────────────────────

    @PostMapping("/filter")
    public ResponseEntity<Page<ProductResponse>> filterProducts(
            @RequestBody ProductFilterRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> productPage = productService.filterProducts(request, pageable);
        return ResponseEntity.ok(productPage.map(this::mapToResponse));
    }

    @GetMapping("/details/{slug}")
    public ResponseEntity<Product> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(productService.getProductBySlug(slug));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(productService.searchProducts(q)
                .stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/category/{slug}")
    public ResponseEntity<Page<ProductResponse>> getByCategory(
            @PathVariable String slug,
            Pageable pageable) {
        return ResponseEntity.ok(productService.getProductsByCategorySlug(slug, pageable)
                .map(this::mapToResponse));
    }

    /**
     * ✅ FIX: Added pagination — the original returned ALL products from MongoDB
     * into memory in one query. With thousands of products this will OOM or time out.
     * Now paginated: default page 0, size 20.
     */
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        ProductFilterRequest emptyFilter = new ProductFilterRequest();
        return ResponseEntity.ok(productService.filterProducts(emptyFilter, pageable)
                .map(this::mapToResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailResponse> getProductById(@PathVariable String id) {
        return ResponseEntity.ok(productService.getProductDetails(id));
    }

    // ── HELPER ────────────────────────────────────────────────────────────────

    private ProductResponse mapToResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .slug(p.getSlug())
                .price(p.getMinPrice() != null ? p.getMinPrice() : p.getBasePrice())
                .stockQuantity(p.getTotalStock() != null ? p.getTotalStock() : 0)
                .categoryName(p.getCategoryName())
                .categorySlug(p.getCategorySlug())
                .brandName(p.getBrandName())
                .images(p.getImages() != null ? p.getImages() : List.of())
                .build();
    }
}