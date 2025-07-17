package semicolon.africa.waylchub;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import semicolon.africa.waylchub.dto.productDto.CreateProductRequest;
import semicolon.africa.waylchub.dto.productDto.ProductResponseDto;
import semicolon.africa.waylchub.dto.productDto.ProductVariantRequest;
import semicolon.africa.waylchub.dto.productDto.SearchProductRequest;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.service.productService.ProductService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductSearchIntegrationTest {

    @Autowired
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void clearDb() {
//        productService.getAllProducts().forEach(p -> productService.deleteProduct(p.getId()));
        productRepository.deleteAll();
    }

    @Test
    @Order(1)
    void shouldReturnCorrectProductsByBrandOnly() {
        for (int i = 1; i <= 3; i++) {
            productService.createProduct(buildProduct("Samsung Galaxy A" + i, "Samsung", "SAM-A" + i));
        }


        for (int i = 1; i <= 3; i++) {
            productService.createProduct(buildProduct("iPhone X" + i, "iPhone", "IPH-X" + i));
        }


        for (int i = 1; i <= 3; i++) {
            productService.createProduct(buildProduct("Nokia N" + i, "Nokia", "NOK-N" + i));
        }


        SearchProductRequest samsungReq = new SearchProductRequest();
        samsungReq.setBrand("Samsung");
        List<ProductResponseDto> samsungResults = productService.searchProducts(samsungReq);
        assertEquals(3, samsungResults.size(), "Samsung count mismatch");


        SearchProductRequest iphoneReq = new SearchProductRequest();
        iphoneReq.setBrand("iPhone");
        List<ProductResponseDto> iphoneResults = productService.searchProducts(iphoneReq);
        assertEquals(3, iphoneResults.size(), "iPhone count mismatch");


        SearchProductRequest nokiaReq = new SearchProductRequest();
        nokiaReq.setBrand("Nokia");
        List<ProductResponseDto> nokiaResults = productService.searchProducts(nokiaReq);
        assertEquals(3, nokiaResults.size(), "Nokia count mismatch");
    }

    private CreateProductRequest buildProduct(String name, String brand, String sku) {
        CreateProductRequest request = new CreateProductRequest();
        request.setName(name);
        request.setBrand(brand);
        request.setCategory("Electronics");
        request.setSubCategory("Phones");
        request.setDescription("Test device");
        request.setTags(List.of("test", "mobile"));

        List<String> images = List.of("https://example.com/img.jpg","https://example.com/img.jpg","https://example.com/img.jpg");

        ProductVariantRequest variant = new ProductVariantRequest();
        variant.setSku(sku);
        variant.setPrice(BigDecimal.valueOf(100000));
        variant.setQuantity(10);
        variant.setAttributes(Map.of("memory", "128GB", "color", "Black"));
        variant.setImageUrls(images);

        request.setVariants(List.of(variant));
        return request;
    }
}

