package semicolon.africa.waylchub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.productDto.CreateProductRequest;
import semicolon.africa.waylchub.dto.productDto.ProductRequest;
import semicolon.africa.waylchub.dto.productDto.ProductResponse;
import semicolon.africa.waylchub.dto.productDto.ProductVariantRequest;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.service.productService.ProductService;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @PostMapping
     @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> addProduct(@Valid @RequestBody ProductRequest request) {
        Product savedProduct = productService.addOrUpdateProduct(request);
        return new ResponseEntity<>(mapToResponse(savedProduct), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts()
                .stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/category/{slug}")
    public ResponseEntity<List<ProductResponse>> getByCategory(@PathVariable String slug) {
        return ResponseEntity.ok(productService.getProductsByCategorySlug(slug)
                .stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(productService.searchProducts(q)
                .stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    // Helper method to keep controller clean
    private ProductResponse mapToResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .slug(p.getSlug())
                .price(p.getPrice())
                .stockQuantity(p.getStockQuantity())
                // Safe check for nulls
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : "Uncategorized")
                .brandName(p.getBrand() != null ? p.getBrand().getName() : "Generic")
                .categorySlug(
                        p.getCategory() != null ? p.getCategory().getSlug() : null
                )

                .build();
    }
}



//@RestController
//@RequestMapping("/api/products")
//@RequiredArgsConstructor
//public class ProductController {
//
//    private final ProductService productService;
//    //private final ProductRepository productRepository;
//
//    @PostMapping("/create-product")
//    @PreAuthorize("hasRole('ROLE_USER')")
//    public ResponseEntity<ProductResponseDto> createProduct(@Valid @RequestBody CreateProductRequest request) {
//
//        return ResponseEntity.ok(productService.createProduct(request));
//    }
//
//    @GetMapping("/{id}")
//    public ResponseEntity<ProductResponseDto> getProductById(@PathVariable String id) {
//        return ResponseEntity.ok(productService.getProductById(id));
//    }
//
//
//    @GetMapping("/sku/{sku}")
//    public ResponseEntity<ProductResponseDto> getProductBySku(@PathVariable String sku) {
//        return ResponseEntity.ok(productService.getProductBySku(sku));
//    }
//
//    @GetMapping
//    public ResponseEntity<List<ProductResponseDto>> getAllProducts() {
//        return ResponseEntity.ok(productService.getAllProducts());
//    }
//
//    @PutMapping("/{id}")
//    public ResponseEntity<ProductResponseDto> updateProduct(
//            @PathVariable String id,
//            @Valid @RequestBody CreateProductRequest request
//    ) {
//        return ResponseEntity.ok(productService.updateProduct(id, request));
//    }
//
//    @DeleteMapping("/{id}")
//    public ResponseEntity<Void> deleteProduct(@PathVariable String id) {
//        productService.deleteProduct(id);
//        return ResponseEntity.noContent().build();
//    }
//
//    @GetMapping("/category/{category}")
//    public ResponseEntity<List<ProductResponseDto>> getByCategory(@PathVariable String category) {
//        return ResponseEntity.ok(productService.getByCategory(category));
//    }
//
//    @GetMapping("/sub-category/{subCategory}")
//    public ResponseEntity<List<ProductResponseDto>> getBySubCategory(@PathVariable String subCategory) {
//        return ResponseEntity.ok(productService.getBySubCategory(subCategory));
//    }
//
//}
