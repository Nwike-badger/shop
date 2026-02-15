//package semicolon.africa.waylchub.controller;
//
//import jakarta.validation.Valid;
//import lombok.RequiredArgsConstructor;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.prepost.PreAuthorize;
//import org.springframework.web.bind.annotation.*;
//import semicolon.africa.waylchub.dto.productDto.ProductFilterRequest;
//import semicolon.africa.waylchub.dto.productDto.ProductRequest;
//import semicolon.africa.waylchub.dto.productDto.ProductResponse;
//import semicolon.africa.waylchub.model.product.Product;
//import semicolon.africa.waylchub.service.productService.ProductService;
//
//import java.util.List;
//import java.util.stream.Collectors;
//
//@RestController
//@RequestMapping("/api/products")
//@RequiredArgsConstructor
//public class ProductController {
//
//    private final ProductService productService;
//
//    @PostMapping
//    // @PreAuthorize("hasRole('ADMIN')") // Uncomment when security is ready
//    public ResponseEntity<ProductResponse> addProduct(@Valid @RequestBody ProductRequest request) {
//        Product savedProduct = productService.addOrUpdateProduct(request);
//        return new ResponseEntity<>(mapToResponse(savedProduct), HttpStatus.CREATED);
//    }
//
//    @PostMapping("/filter")
//    public ResponseEntity<Page<ProductResponse>> filterProducts(
//            @RequestBody ProductFilterRequest request,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size
//    ) {
//        Pageable pageable = PageRequest.of(page, size);
//
//        Page<Product> productPage = productService.filterProducts(request, pageable);
//
//
//        Page<ProductResponse> responsePage = productPage.map(this::mapToResponse);
//
//        return ResponseEntity.ok(responsePage);
//    }
//
//    @GetMapping
//    public ResponseEntity<List<ProductResponse>> getAllProducts() {
//        return ResponseEntity.ok(productService.getAllProducts()
//                .stream().map(this::mapToResponse).collect(Collectors.toList()));
//    }
//
//    @GetMapping("/category/{slug}")
//    public ResponseEntity<List<ProductResponse>> getByCategory(@PathVariable String slug) {
//        return ResponseEntity.ok(productService.getProductsByCategorySlug(slug)
//                .stream().map(this::mapToResponse).collect(Collectors.toList()));
//    }
//
//    @GetMapping("/search")
//    public ResponseEntity<List<ProductResponse>> search(@RequestParam String q) {
//        return ResponseEntity.ok(productService.searchProducts(q)
//                .stream().map(this::mapToResponse).collect(Collectors.toList()));
//    }
//
//
//
//    private ProductResponse mapToResponse(Product p) {
//        return ProductResponse.builder()
//                .id(p.getId())
//                .name(p.getName())
//                .slug(p.getSlug())
//                .price(p.getPrice())
//                .stockQuantity(p.getStockQuantity())
//                .categoryName(p.getCategoryName()) // Use denormalized name
//                .categorySlug(p.getCategorySlug())
//                .images(p.getImages() != null ? p.getImages() : List.of())
//                .brandName(p.getBrand() != null ? p.getBrand().getName() : "Generic")
//                .build();
//    }
//
//
//
//
//
//}