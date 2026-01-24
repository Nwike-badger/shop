package semicolon.africa.waylchub;

class ProductServiceImplTest {

//    @Mock
//    private ProductRepository productRepository;
//
//    @InjectMocks
//    private ProductServiceImpl productService;
//
//    @BeforeEach
//    void setup() {
//        MockitoAnnotations.openMocks(this);
//    }
//
//    @Test
//    void testCreateProductSuccess() {
//
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
//
//        when(productRepository.findByNameAndCategory("Samsung Galaxy A55", "Electronics"))
//                .thenReturn(Optional.empty());
//        when(productRepository.findByVariants_Sku(anyString()))
//                .thenReturn(Optional.empty());
//
//
//        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
//        when(productRepository.save(productCaptor.capture()))
//                .thenAnswer(invocation -> invocation.getArgument(0)); // Return same object
//
//
//        ProductResponseDto response = productService.createProduct(request);
//
//
//        assertNotNull(response);
//        assertEquals("Samsung Galaxy A55", response.getName());
//        assertEquals(2, response.getVariants().size());
//
//
//        System.out.println("✅ Created Product ID: " + response.getId());
//        System.out.println("✅ Name: " + response.getName());
//        System.out.println("✅ Variants: " + response.getVariants().size());
//
//
//        verify(productRepository).save(any(Product.class));
//    }
}

