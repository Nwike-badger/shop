package semicolon.africa.waylchub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.productDto.CreateProductRequest;
import semicolon.africa.waylchub.dto.productDto.ProductResponseDto;
import semicolon.africa.waylchub.dto.productDto.ProductVariantRequest;
import semicolon.africa.waylchub.service.productService.ProductService;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponseDto> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.ok(productService.createProduct(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDto> getProductById(@PathVariable String id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<ProductResponseDto> getProductBySku(@PathVariable String sku) {
        return ResponseEntity.ok(productService.getProductBySku(sku));
    }

    @GetMapping
    public ResponseEntity<List<ProductResponseDto>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDto> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody CreateProductRequest request
    ) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<ProductResponseDto>> getByCategory(@PathVariable String category) {
        return ResponseEntity.ok(productService.getByCategory(category));
    }

    @GetMapping("/sub-category/{subCategory}")
    public ResponseEntity<List<ProductResponseDto>> getBySubCategory(@PathVariable String subCategory) {
        return ResponseEntity.ok(productService.getBySubCategory(subCategory));
    }

    @PatchMapping("/variant/{sku}/quantity")
    public ResponseEntity<ProductResponseDto> updateProductVariantQuantity(
            @PathVariable String sku,
            @RequestParam int quantityChange
    ) {
        return ResponseEntity.ok(productService.updateProductVariantQuantity(sku, quantityChange));
    }

    @PostMapping("/{productId}/variant")
    public ResponseEntity<ProductResponseDto> addVariantToProduct(
            @PathVariable String productId,
            @Valid @RequestBody ProductVariantRequest variantRequest
    ) {
        return ResponseEntity.ok(productService.addVariantToProduct(productId, variantRequest));
    }

    @PutMapping("/{productId}/variant/{sku}")
    public ResponseEntity<ProductResponseDto> updateProductVariant(
            @PathVariable String productId,
            @PathVariable String sku,
            @Valid @RequestBody ProductVariantRequest variantRequest
    ) {
        return ResponseEntity.ok(productService.updateProductVariant(productId, sku, variantRequest));
    }

    @DeleteMapping("/{productId}/variant/{sku}")
    public ResponseEntity<Void> deleteProductVariant(
            @PathVariable String productId,
            @PathVariable String sku
    ) {
        productService.deleteProductVariant(productId, sku);
        return ResponseEntity.noContent().build();
    }
}
