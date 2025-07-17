package semicolon.africa.waylchub;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import semicolon.africa.waylchub.dto.productDto.*;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.service.productService.ProductService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ProductServiceIntegrationTest {

//    @Autowired
//    private ProductService productService;
//
//    @Autowired
//    private ProductRepository productRepository;
//
//    @BeforeEach
//    public void cleanDatabase() {
//        productRepository.deleteAll();
//    }
//
//    @Test
//    public void testCreateProduct_withVariants_shouldSucceed() {
//        CreateProductRequest request = new CreateProductRequest();
//        request.setName("Samsung Galaxy A55");
//        request.setDescription("Latest budget-friendly Samsung smartphone.");
//        request.setCategory("Electronics");
//        request.setSubCategory("Phones");
//        request.setTags(List.of("smartphone", "android", "samsung"));
//        request.setBrand("Samsung");
//
//        ProductVariantRequest variant1 = new ProductVariantRequest();
//        variant1.setSku("SAM-A55-128GB-BLUE");
//        variant1.setPrice(new BigDecimal("220000"));
//        variant1.setQuantity(20);
//        variant1.setAttributes(Map.of("color", "Blue", "memory", "128GB"));
//        variant1.setImageUrl("https://example.com/images/a55-blue.jpg");
//
//        ProductVariantRequest variant2 = new ProductVariantRequest();
//        variant2.setSku("SAM-A55-256GB-BLACK");
//        variant2.setPrice(new BigDecimal("250000"));
//        variant2.setQuantity(15);
//        variant2.setAttributes(Map.of("color", "Black", "memory", "256GB"));
//        variant2.setImageUrl("https://example.com/images/a55-black.jpg");
//
//        request.setVariants(List.of(variant1, variant2));
//
//        ProductResponseDto response = productService.createProduct(request);
//
//        System.out.println("✅ Product Created ID: " + response.getId());
//        System.out.println("✅ Name: " + response.getName());
//        System.out.println("✅ Variant Count: " + response.getVariants().size());
//
//        assertNotNull(response.getId());
//        assertEquals("Samsung Galaxy A55", response.getName());
//        assertEquals(2, response.getVariants().size());
//    }
}
