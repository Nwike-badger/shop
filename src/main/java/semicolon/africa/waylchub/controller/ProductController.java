package semicolon.africa.waylchub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.productDto.ProductFilterRequest;
import semicolon.africa.waylchub.dto.productDto.ProductRequest;
import semicolon.africa.waylchub.dto.productDto.ProductResponse;
import semicolon.africa.waylchub.dto.productDto.VariantRequest;
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

    // --- 1. PRODUCT MANAGEMENT ---

    @PostMapping
    public ResponseEntity<ProductResponse> addProduct(@Valid @RequestBody ProductRequest request) {
        Product savedProduct = productService.createOrUpdateProduct(request);
        return new ResponseEntity<>(mapToResponse(savedProduct), HttpStatus.CREATED);
    }

    // 🔥 NEW: Variant Endpoint (This was missing!)
    @PostMapping("/variants")
    public ResponseEntity<ProductVariant> addVariant(@Valid @RequestBody VariantRequest request) {
        ProductVariant savedVariant = productService.saveVariant(request);
        return new ResponseEntity<>(savedVariant, HttpStatus.CREATED);
    }

    // --- 2. SEARCH & FILTER ---

    @PostMapping("/filter")
    public ResponseEntity<Page<ProductResponse>> filterProducts(
            @RequestBody ProductFilterRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> productPage = productService.filterProducts(request, pageable);
        return ResponseEntity.ok(productPage.map(this::mapToResponse));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> search(@RequestParam String q) {
        // Uses the new adapter in service
        return ResponseEntity.ok(productService.searchProducts(q)
                .stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/category/{slug}")
    public ResponseEntity<List<ProductResponse>> getByCategory(@PathVariable String slug) {
        // Uses the new adapter in service
        return ResponseEntity.ok(productService.getProductsByCategorySlug(slug)
                .stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts()
                .stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable String id) {
        Product product = productService.getProductById(id); // You might need to add this to Service if missing
        return ResponseEntity.ok(product);
    }
    // --- 3. MAPPER (Fixed for New Model) ---

    private ProductResponse mapToResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .slug(p.getSlug())
                // Use calculated min price for "From X" display
                .price(p.getMinPrice() != null ? p.getMinPrice() : p.getBasePrice())
                // Use aggregated total stock
                .stockQuantity(p.getTotalStock() != null ? p.getTotalStock() : 0)
                .categoryName(p.getCategoryName())
                .categorySlug(p.getCategorySlug())
                .brandName(p.getBrandName())
                .images(p.getImages() != null ? p.getImages() : List.of())
                .build();
    }
}