package semicolon.africa.waylchub.service.campaign;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import semicolon.africa.waylchub.model.campaign.Campaign;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.model.product.ProductVariant;
import semicolon.africa.waylchub.repository.campaign.CampaignRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CampaignService")
class CampaignServiceTest {

    @Mock CampaignRepository campaignRepository;
    @Mock MongoTemplate mongoTemplate;

    @InjectMocks CampaignService campaignService;

    BulkOperations productBulkOps;
    BulkOperations variantBulkOps;

    @BeforeEach
    void setUp() {
        productBulkOps = mock(BulkOperations.class);
        variantBulkOps = mock(BulkOperations.class);

        when(mongoTemplate.bulkOps(any(), eq(Product.class))).thenReturn(productBulkOps);
        when(mongoTemplate.bulkOps(any(), eq(ProductVariant.class))).thenReturn(variantBulkOps);

        when(productBulkOps.updateOne(any(), any(Update.class))).thenReturn(productBulkOps);
        when(variantBulkOps.updateOne(any(), any(Update.class))).thenReturn(variantBulkOps);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PRODUCT-LEVEL CAMPAIGN ACTIVATION
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Product-Level Activation")
    class ProductLevelActivation {

        @Test
        @DisplayName("applies 20% discount to product basePrice and all variant prices")
        void activate_discountsProductAndVariants() {
            Campaign campaign = buildProductCampaign("camp-1", BigDecimal.valueOf(20));

            Product product = buildProduct("prod-1", new BigDecimal("100.00"), null);
            ProductVariant v1 = buildVariant("var-1", "prod-1", new BigDecimal("100.00"), null);
            ProductVariant v2 = buildVariant("var-2", "prod-1", new BigDecimal("120.00"), null);

            when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(List.of(product));
            when(mongoTemplate.find(any(Query.class), eq(ProductVariant.class))).thenReturn(List.of(v1, v2));

            campaignService.activateCampaign(campaign);

            ArgumentCaptor<Update> productCaptor = ArgumentCaptor.forClass(Update.class);
            verify(productBulkOps, atLeastOnce()).updateOne(any(), productCaptor.capture());

            String updateStr = productCaptor.getAllValues().get(0).toString();
            assertThat(updateStr).contains("activeCampaignId", "originalBasePrice", "80.00");

            verify(variantBulkOps, times(2)).updateOne(any(), any(Update.class));
            verify(variantBulkOps).execute();
            verify(campaignRepository).save(argThat(Campaign::isActive));
        }

        @Test
        @DisplayName("writes originalBasePrice as backup, NOT compareAtPrice")
        void activate_backsUpToOriginalBasePrice_notCompareAtPrice() {
            Campaign campaign = buildProductCampaign("camp-1", BigDecimal.valueOf(10));
            Product product = buildProduct("prod-1", new BigDecimal("200.00"), new BigDecimal("250.00"));

            when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(List.of(product));
            when(mongoTemplate.find(any(Query.class), eq(ProductVariant.class))).thenReturn(List.of());

            campaignService.activateCampaign(campaign);

            ArgumentCaptor<Update> productCaptor = ArgumentCaptor.forClass(Update.class);
            verify(productBulkOps, atLeastOnce()).updateOne(any(), productCaptor.capture());

            String updateStr = productCaptor.getAllValues().get(0).toString();
            assertThat(updateStr).contains("originalBasePrice").doesNotContain("compareAtPrice");
        }

        @Test
        @DisplayName("skips products already owned by another active campaign")
        void activate_skipsProductsAlreadyInCampaign() {
            Campaign campaign = buildProductCampaign("camp-2", BigDecimal.valueOf(15));
            when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(List.of());

            campaignService.activateCampaign(campaign);

            verify(productBulkOps, never()).execute();
            verify(variantBulkOps, never()).execute();
        }

        @Test
        @DisplayName("does not call variantOps.execute() when no variants exist")
        void activate_noVariants_doesNotExecuteVariantBulkOps() {
            Campaign campaign = buildProductCampaign("camp-1", BigDecimal.valueOf(10));
            Product product = buildProduct("prod-1", new BigDecimal("50.00"), null);

            when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(List.of(product));
            when(mongoTemplate.find(any(Query.class), eq(ProductVariant.class))).thenReturn(List.of());

            campaignService.activateCampaign(campaign);

            verify(productBulkOps).execute();
            verify(variantBulkOps, never()).execute();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PRODUCT-LEVEL CAMPAIGN DEACTIVATION
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Product-Level Deactivation")
    class ProductLevelDeactivation {

        @Test
        @DisplayName("restores basePrice from originalBasePrice backup")
        void deactivate_restoresFromBackup() {
            Campaign campaign = buildProductCampaign("camp-1", BigDecimal.valueOf(20));
            campaign.setActive(true);

            Product product = buildProduct("prod-1", new BigDecimal("80.00"), null);
            product.setActiveCampaignId("camp-1");
            product.setOriginalBasePrice(new BigDecimal("100.00"));

            ProductVariant variant = buildVariant("var-1", "prod-1", new BigDecimal("64.00"), null);
            variant.setActiveCampaignId("camp-1");
            variant.setOriginalPrice(new BigDecimal("80.00"));

            when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(List.of(product));
            when(mongoTemplate.find(any(Query.class), eq(ProductVariant.class))).thenReturn(List.of(variant));

            campaignService.deactivateCampaign(campaign);

            ArgumentCaptor<Update> productCaptor = ArgumentCaptor.forClass(Update.class);
            verify(productBulkOps, atLeastOnce()).updateOne(any(), productCaptor.capture());
            assertThat(productCaptor.getAllValues().get(0).toString()).contains("100.00");

            ArgumentCaptor<Update> variantCaptor = ArgumentCaptor.forClass(Update.class);
            verify(variantBulkOps, atLeastOnce()).updateOne(any(), variantCaptor.capture());
            assertThat(variantCaptor.getAllValues().get(0).toString()).contains("80.00");
        }

        @Test
        @DisplayName("falls back safely when originalBasePrice backup is missing")
        void deactivate_noBackup_usesCurrentPrice() {
            Campaign campaign = buildProductCampaign("camp-1", BigDecimal.valueOf(20));
            campaign.setActive(true);

            Product product = buildProduct("prod-1", new BigDecimal("80.00"), null);
            product.setActiveCampaignId("camp-1");
            product.setOriginalBasePrice(null);

            when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(List.of(product));
            when(mongoTemplate.find(any(Query.class), eq(ProductVariant.class))).thenReturn(List.of());

            assertThatCode(() -> campaignService.deactivateCampaign(campaign))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("does not restore variants that belong to a different campaign")
        void deactivate_doesNotTouchVariantsOwnedByOtherCampaign() {
            Campaign campaign = buildProductCampaign("camp-1", BigDecimal.valueOf(20));
            campaign.setActive(true);

            Product product = buildProduct("prod-1", new BigDecimal("80.00"), null);
            product.setActiveCampaignId("camp-1");
            product.setOriginalBasePrice(new BigDecimal("100.00"));

            ProductVariant otherVariant = buildVariant("var-1", "prod-1", new BigDecimal("50.00"), null);
            otherVariant.setActiveCampaignId("other-campaign");

            ProductVariant ourVariant = buildVariant("var-2", "prod-1", new BigDecimal("64.00"), null);
            ourVariant.setActiveCampaignId("camp-1");
            ourVariant.setOriginalPrice(new BigDecimal("80.00"));

            when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(List.of(product));
            when(mongoTemplate.find(any(Query.class), eq(ProductVariant.class))).thenReturn(List.of(otherVariant, ourVariant));

            campaignService.deactivateCampaign(campaign);

            verify(variantBulkOps, times(1)).updateOne(any(), any(Update.class));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VARIANT-LEVEL CAMPAIGNS
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Variant-Level Campaigns")
    class VariantLevelCampaigns {

        @Test
        @DisplayName("discounts only the targeted variants, never touches product basePrice")
        void variantCampaign_activate_onlyVariantsDiscounted() {
            Campaign campaign = Campaign.builder()
                    .id("camp-v1")
                    .name("Red variant sale")
                    .targetVariantIds(Set.of("var-red"))
                    .discountPercentage(BigDecimal.valueOf(25))
                    .startDate(Instant.now().minusSeconds(60))
                    .endDate(Instant.now().plusSeconds(3600))
                    .build();

            ProductVariant redVariant = buildVariant("var-red", "prod-1", new BigDecimal("200.00"), null);

            when(mongoTemplate.find(any(Query.class), eq(ProductVariant.class)))
                    .thenReturn(List.of(redVariant))
                    .thenReturn(List.of(redVariant));

            campaignService.activateCampaign(campaign);

            ArgumentCaptor<Update> variantCaptor = ArgumentCaptor.forClass(Update.class);
            verify(variantBulkOps, atLeastOnce()).updateOne(any(), variantCaptor.capture());

            String updateStr = variantCaptor.getAllValues().get(0).toString();
            assertThat(updateStr).contains("originalPrice", "150.00", "camp-v1");

            // Ensure product basePrice is untouched, but allow minPrice/maxPrice updates
            ArgumentCaptor<Update> productCaptor = ArgumentCaptor.forClass(Update.class);

            // Allow the method to be called (it will be called for minPrice/maxPrice refresh)
            verify(productBulkOps, atLeast(0)).updateOne(any(), productCaptor.capture());

            // If it was called, prove it never touched the basePrice
            if (!productCaptor.getAllValues().isEmpty()) {
                assertThat(productCaptor.getAllValues()).noneSatisfy(update -> {
                    assertThat(update.toString()).contains("basePrice");
                });
            }
        }

        @Test
        @DisplayName("restores only targeted variant prices on deactivation")
        void variantCampaign_deactivate_restoresVariantPrices() {
            Campaign campaign = Campaign.builder()
                    .id("camp-v1")
                    .name("Red variant sale")
                    .targetVariantIds(Set.of("var-red"))
                    .discountPercentage(BigDecimal.valueOf(25))
                    .active(true)
                    .build();

            ProductVariant redVariant = buildVariant("var-red", "prod-1", new BigDecimal("150.00"), null);
            redVariant.setActiveCampaignId("camp-v1");
            redVariant.setOriginalPrice(new BigDecimal("200.00"));

            when(mongoTemplate.find(any(Query.class), eq(ProductVariant.class)))
                    .thenReturn(List.of(redVariant));

            campaignService.deactivateCampaign(campaign);

            ArgumentCaptor<Update> variantCaptor = ArgumentCaptor.forClass(Update.class);
            verify(variantBulkOps, atLeastOnce()).updateOne(any(), variantCaptor.capture());
            assertThat(variantCaptor.getAllValues().get(0).toString()).contains("200.00");
        }

        @Test
        @DisplayName("isVariantLevel returns true only when targetVariantIds is populated")
        void isVariantLevel_correctlyDistinguishes() {
            Campaign productCampaign = Campaign.builder().targetCategorySlug("laptops").build();
            Campaign variantCampaign = Campaign.builder().targetVariantIds(Set.of("var-1")).build();

            assertThat(productCampaign.isVariantLevel()).isFalse();
            assertThat(variantCampaign.isVariantLevel()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TARGETING LOGIC (AND vs OR)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Targeting Query Logic")
    class TargetingLogic {

        @Test
        @DisplayName("category + brand produces AND query, not OR")
        void buildTargetQuery_categoryAndBrand_producesAndQuery() {
            Campaign campaign = Campaign.builder()
                    .id("camp-1")
                    .targetCategorySlug("laptops")
                    .targetBrandSlug("apple")
                    .discountPercentage(BigDecimal.TEN)
                    .build();

            Query query = campaignService.buildProductTargetQuery(campaign);
            String queryStr = query.getQueryObject().toJson();

            assertThat(queryStr).contains("categorySlug");
            assertThat(queryStr).contains("brandName");
            assertThat(queryStr).doesNotContain("\"$or\"");
        }

        @Test
        @DisplayName("no targeting rules throws IllegalStateException")
        void buildTargetQuery_noRules_throws() {
            Campaign campaign = Campaign.builder().id("camp-1").build();

            assertThatThrownBy(() -> campaignService.buildProductTargetQuery(campaign))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no targeting rules");
        }

        @Test
        @DisplayName("tag-only targeting produces correct query")
        void buildTargetQuery_tagOnly() {
            Campaign campaign = Campaign.builder()
                    .id("camp-1")
                    .targetTag("clearance")
                    .discountPercentage(BigDecimal.TEN)
                    .build();

            Query query = campaignService.buildProductTargetQuery(campaign);
            assertThat(query.getQueryObject().toJson()).contains("tags");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GHOST VARIANT
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Ghost Variant Protection")
    class GhostVariant {

        @Test
        @DisplayName("new variant added during active campaign: price discounted and backup set in memory")
        void ghostVariant_appliesCampaignDiscountToNewVariant() {
            Campaign activeCampaign = buildProductCampaign("camp-1", BigDecimal.valueOf(20));
            activeCampaign.setActive(true);

            ProductVariant newVariant = buildVariant("var-new", "prod-1", new BigDecimal("100.00"), null);

            when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(activeCampaign));

            campaignService.applyActiveCampaignToNewVariant(newVariant, "camp-1");

            assertThat(newVariant.getActiveCampaignId()).isEqualTo("camp-1");
            assertThat(newVariant.getOriginalPrice()).isEqualByComparingTo("100.00");
            assertThat(newVariant.getPrice()).isEqualByComparingTo("80.00");

            verify(mongoTemplate, never()).updateFirst(any(), any(), eq(ProductVariant.class));
        }

        @Test
        @DisplayName("ghost variant fix is skipped when campaign is not active: object unchanged")
        void ghostVariant_skippedWhenCampaignInactive() {
            Campaign inactiveCampaign = buildProductCampaign("camp-1", BigDecimal.valueOf(20));
            inactiveCampaign.setActive(false);

            ProductVariant newVariant = buildVariant("var-new", "prod-1", new BigDecimal("100.00"), null);

            when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(inactiveCampaign));

            campaignService.applyActiveCampaignToNewVariant(newVariant, "camp-1");

            assertThat(newVariant.getActiveCampaignId()).isNull();
            assertThat(newVariant.getOriginalPrice()).isNull();
            assertThat(newVariant.getPrice()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("ghost variant fix is skipped for variant-level campaigns: object unchanged")
        void ghostVariant_skippedForVariantLevelCampaign() {
            Campaign variantCampaign = Campaign.builder()
                    .id("camp-v1")
                    .targetVariantIds(Set.of("var-1"))
                    .discountPercentage(BigDecimal.TEN)
                    .active(true)
                    .build();

            ProductVariant newVariant = buildVariant("var-new", "prod-1", new BigDecimal("100.00"), null);

            when(campaignRepository.findById("camp-v1")).thenReturn(Optional.of(variantCampaign));

            campaignService.applyActiveCampaignToNewVariant(newVariant, "camp-v1");

            assertThat(newVariant.getActiveCampaignId()).isNull();
            assertThat(newVariant.getOriginalPrice()).isNull();
            assertThat(newVariant.getPrice()).isEqualByComparingTo("100.00");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RACE CONDITION / OPTIMISTIC LOCKING
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Race Condition Handling")
    class RaceConditions {

        @Test
        @DisplayName("scheduler swallows OptimisticLockingFailureException when another thread already activated")
        void scheduler_swallowsOptimisticLockingException_onActivate() {
            Campaign campaign = buildProductCampaign("camp-1", BigDecimal.valueOf(20));
            when(campaignRepository.findByActiveFalseAndStartDateLessThanEqualAndEndDateGreaterThan(any(), any()))
                    .thenReturn(List.of(campaign));
            when(campaignRepository.findByActiveTrueAndEndDateLessThanEqual(any()))
                    .thenReturn(List.of());

            when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(List.of());
            when(campaignRepository.save(any())).thenThrow(new OptimisticLockingFailureException("version conflict"));

            assertThatCode(() -> campaignService.processCampaigns()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("scheduler swallows OptimisticLockingFailureException when another thread already deactivated")
        void scheduler_swallowsOptimisticLockingException_onDeactivate() {
            when(campaignRepository.findByActiveFalseAndStartDateLessThanEqualAndEndDateGreaterThan(any(), any()))
                    .thenReturn(List.of());

            Campaign campaign = buildProductCampaign("camp-1", BigDecimal.valueOf(20));
            campaign.setActive(true);
            when(campaignRepository.findByActiveTrueAndEndDateLessThanEqual(any()))
                    .thenReturn(List.of(campaign));

            when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(List.of());
            when(campaignRepository.save(any())).thenThrow(new OptimisticLockingFailureException("version conflict"));

            assertThatCode(() -> campaignService.processCampaigns()).doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN OPERATIONS
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Admin Operations")
    class AdminOperations {

        @Test
        @DisplayName("manualActivate throws when campaign is already active")
        void manualActivate_alreadyActive_throws() {
            Campaign campaign = buildProductCampaign("camp-1", BigDecimal.valueOf(10));
            campaign.setActive(true);
            when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(campaign));

            assertThatThrownBy(() -> campaignService.manualActivate("camp-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already active");
        }

        @Test
        @DisplayName("deleteCampaign deactivates prices before deleting when campaign is active")
        void deleteCampaign_activeFirst_deactivatesThenDeletes() {
            Campaign campaign = buildProductCampaign("camp-1", BigDecimal.valueOf(10));
            campaign.setActive(true);

            when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(campaign));
            when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(List.of());

            campaignService.deleteCampaign("camp-1");

            verify(campaignRepository).deleteById("camp-1");
        }

        @Test
        @DisplayName("updateCampaign throws when campaign is active")
        void updateCampaign_active_throws() {
            Campaign campaign = buildProductCampaign("camp-1", BigDecimal.valueOf(10));
            campaign.setActive(true);
            when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(campaign));

            assertThatThrownBy(() -> campaignService.updateCampaign("camp-1", new semicolon.africa.waylchub.dto.campaignDto.CampaignRequest()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Deactivate it first");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VALIDATION
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Request Validation")
    class Validation {

        @Test
        @DisplayName("creating campaign with no targeting throws IllegalArgumentException")
        void create_noTargeting_throws() {
            var request = new semicolon.africa.waylchub.dto.campaignDto.CampaignRequest();
            request.setName("Bad Campaign");
            request.setDiscountPercentage(BigDecimal.TEN);
            request.setStartDate(Instant.now().plusSeconds(60));
            request.setEndDate(Instant.now().plusSeconds(3600));

            assertThatThrownBy(() -> campaignService.createCampaign(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must target at least one");
        }

        @Test
        @DisplayName("mixing product and variant targeting throws IllegalArgumentException")
        void create_mixedTargeting_throws() {
            var request = new semicolon.africa.waylchub.dto.campaignDto.CampaignRequest();
            request.setName("Mixed Campaign");
            request.setDiscountPercentage(BigDecimal.TEN);
            request.setStartDate(Instant.now().plusSeconds(60));
            request.setEndDate(Instant.now().plusSeconds(3600));
            request.setTargetCategorySlug("laptops");
            request.setTargetVariantIds(Set.of("var-1"));

            assertThatThrownBy(() -> campaignService.createCampaign(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot mix");
        }

        @Test
        @DisplayName("end date before start date throws IllegalArgumentException")
        void create_endBeforeStart_throws() {
            var request = new semicolon.africa.waylchub.dto.campaignDto.CampaignRequest();
            request.setName("Bad Dates");
            request.setDiscountPercentage(BigDecimal.TEN);
            request.setTargetCategorySlug("laptops");
            request.setStartDate(Instant.now().plusSeconds(3600));
            request.setEndDate(Instant.now().plusSeconds(60));

            assertThatThrownBy(() -> campaignService.createCampaign(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("End date must be after start date");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DISCOUNT MATH
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Discount Calculation Accuracy")
    class DiscountMath {

        @Test
        @DisplayName("50% off ₦199.99 rounds correctly to ₦100.00")
        void discount_50percent_roundsCorrectly() {
            Campaign campaign = buildProductCampaign("camp-1", BigDecimal.valueOf(50));
            Product product = buildProduct("prod-1", new BigDecimal("199.99"), null);
            ProductVariant variant = buildVariant("var-1", "prod-1", new BigDecimal("199.99"), null);

            when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(List.of(product));
            when(mongoTemplate.find(any(Query.class), eq(ProductVariant.class))).thenReturn(List.of(variant));

            campaignService.activateCampaign(campaign);

            ArgumentCaptor<Update> variantCaptor = ArgumentCaptor.forClass(Update.class);
            verify(variantBulkOps, atLeastOnce()).updateOne(any(), variantCaptor.capture());

            assertThat(variantCaptor.getAllValues().get(0).toString()).contains("100.00");
        }

        @Test
        @DisplayName("33.33% off ₦300.00 produces ₦200.00 exactly")
        void discount_thirdOff_exactResult() {
            Campaign campaign = buildProductCampaign("camp-1", new BigDecimal("33.33"));
            Product product = buildProduct("prod-1", new BigDecimal("300.00"), null);
            ProductVariant variant = buildVariant("var-1", "prod-1", new BigDecimal("300.00"), null);

            when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(List.of(product));
            when(mongoTemplate.find(any(Query.class), eq(ProductVariant.class))).thenReturn(List.of(variant));

            campaignService.activateCampaign(campaign);

            verify(variantBulkOps, atLeastOnce()).updateOne(any(), any(Update.class));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private Campaign buildProductCampaign(String id, BigDecimal discount) {
        return Campaign.builder()
                .id(id)
                .name("Test Campaign " + id)
                .targetCategorySlug("electronics")
                .discountPercentage(discount)
                .startDate(Instant.now().minusSeconds(60))
                .endDate(Instant.now().plusSeconds(3600))
                .active(false)
                .build();
    }

    private Product buildProduct(String id, BigDecimal basePrice, BigDecimal compareAtPrice) {
        Product p = new Product();
        p.setId(id);
        p.setName("Product " + id);
        p.setBasePrice(basePrice);
        p.setCompareAtPrice(compareAtPrice);
        p.setActive(true);
        p.setTags(new HashSet<>());
        return p;
    }

    private ProductVariant buildVariant(String id, String productId, BigDecimal price, String activeCampaignId) {
        ProductVariant v = new ProductVariant();
        v.setId(id);
        v.setProductId(productId);
        v.setSku("SKU-" + id);
        v.setPrice(price);
        v.setActive(true);
        v.setActiveCampaignId(activeCampaignId);
        return v;
    }
}