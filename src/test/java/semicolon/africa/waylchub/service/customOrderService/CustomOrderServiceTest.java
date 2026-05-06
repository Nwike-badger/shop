package semicolon.africa.waylchub.service.customOrderService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import semicolon.africa.waylchub.dto.customOrderDto.CustomOrderRequest;
import semicolon.africa.waylchub.dto.customOrderDto.QuoteRequest;
import semicolon.africa.waylchub.dto.customOrderDto.StyleImageInput;
import semicolon.africa.waylchub.event.CustomOrderQuotedEvent;
import semicolon.africa.waylchub.event.CustomOrderSubmittedEvent;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.customOrder.*;
import semicolon.africa.waylchub.model.order.Address;
import semicolon.africa.waylchub.repository.customOrderRepository.CustomOrderRepository;
import semicolon.africa.waylchub.service.storage.StorageService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOrderServiceTest {

    @Mock private CustomOrderRepository repository;
    @Mock private CustomReferenceGenerator referenceGenerator;
    @Mock private StorageService storageService;
    @Mock private ApplicationEventPublisher eventPublisher;

    // Use a real registry — categories are static data, no value in mocking
    private final CustomCategoryRegistry registry = new CustomCategoryRegistry();

    private CustomOrderService service;

    @BeforeEach
    void setUp() {
        service = new CustomOrderService(repository, referenceGenerator, registry,
                storageService, eventPublisher);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private CustomOrderRequest validRequest() {
        return CustomOrderRequest.builder()
                .categoryId("agbada")
                .gender(Gender.MEN)
                .selectedStyleId("agbada-classic")
                .sizeMode(SizeMode.CHART)
                .chartSize("L")
                .fitting(FittingPreference.REGULAR)
                .customerName("John Doe")
                .whatsappNumber("+2348012345678")
                .deliveryMode(DeliveryMode.ABA)
                .build();
    }

    private CustomOrder savedOrder(CustomOrderRequest req) {
        return CustomOrder.builder()
                .id("507f1f77bcf86cd799439011")
                .referenceNumber("EAB-CD-20260501-X7K2")
                .categoryId(req.getCategoryId())
                .categoryName("Agbada")
                .gender(req.getGender())
                .customerName(req.getCustomerName())
                .whatsappNumber(req.getWhatsappNumber())
                .status(CustomOrderStatus.SUBMITTED)
                .pricing(PricingSpec.builder().build())
                .build();
    }

    // ═════════════════════════════════════════════════════════════════════
    //  SUBMIT
    // ═════════════════════════════════════════════════════════════════════

    @Nested
    class Submit {

        @Test
        @DisplayName("Persists a valid order and publishes submitted event")
        void valid_persists_and_publishes() {
            CustomOrderRequest req = validRequest();
            when(referenceGenerator.generate()).thenReturn("EAB-CD-20260501-X7K2");
            when(repository.save(any(CustomOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            CustomOrder result = service.submit(req, null);

            assertThat(result.getReferenceNumber()).isEqualTo("EAB-CD-20260501-X7K2");
            assertThat(result.getStatus()).isEqualTo(CustomOrderStatus.SUBMITTED);
            assertThat(result.getCategoryName()).isEqualTo("Agbada");
            assertThat(result.getStatusHistory()).hasSize(1);
            verify(eventPublisher).publishEvent(any(CustomOrderSubmittedEvent.class));
        }

        @Test
        @DisplayName("Authenticated submission stamps customerId")
        void authenticated_setsCustomerId() {
            CustomOrderRequest req = validRequest();
            when(referenceGenerator.generate()).thenReturn("EAB-CD-20260501-X7K2");
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomOrder result = service.submit(req, "user-123");

            assertThat(result.getCustomerId()).isEqualTo("user-123");
        }

        @Test
        @DisplayName("Idempotency key returns existing order on duplicate submit")
        void idempotency_returnsExisting() {
            CustomOrderRequest req = validRequest();
            req.setIdempotencyKey("client-uuid-123");

            CustomOrder existing = savedOrder(req);
            when(repository.findByIdempotencyKey("client-uuid-123"))
                    .thenReturn(Optional.of(existing));

            CustomOrder result = service.submit(req, null);

            assertThat(result).isSameAs(existing);
            verify(repository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Rejects unknown category")
        void unknownCategory_rejected() {
            CustomOrderRequest req = validRequest();
            req.setCategoryId("space-suit");

            assertThatThrownBy(() -> service.submit(req, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Requires either selected style or uploaded references")
        void noStyleOrUploads_rejected() {
            CustomOrderRequest req = validRequest();
            req.setSelectedStyleId(null);
            req.setReferenceImages(null);

            assertThatThrownBy(() -> service.submit(req, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("style");
        }

        @Test
        @DisplayName("CHART mode requires chartSize")
        void chartMode_withoutSize_rejected() {
            CustomOrderRequest req = validRequest();
            req.setSizeMode(SizeMode.CHART);
            req.setChartSize(null);

            assertThatThrownBy(() -> service.submit(req, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Chart size");
        }

        @Test
        @DisplayName("MANUAL mode requires at least 4 measurements")
        void manualMode_withFewMeasurements_rejected() {
            CustomOrderRequest req = validRequest();
            req.setSizeMode(SizeMode.MANUAL);
            req.setChartSize(null);
            req.setMeasurements(Map.of("chest", "40", "waist", "32"));  // only 2

            assertThatThrownBy(() -> service.submit(req, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 4 measurements");
        }

        @Test
        @DisplayName("MANUAL mode succeeds with 4+ measurements")
        void manualMode_withEnoughMeasurements_succeeds() {
            CustomOrderRequest req = validRequest();
            req.setSizeMode(SizeMode.MANUAL);
            req.setChartSize(null);
            req.setMeasurements(Map.of(
                    "chest", "40", "waist", "32", "shoulder", "18", "sleeve", "25"));

            when(referenceGenerator.generate()).thenReturn("EAB-CD-20260501-X7K2");
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomOrder result = service.submit(req, null);
            assertThat(result.getSize().getMeasurements()).hasSize(4);
        }

        @Test
        @DisplayName("TAILOR_VISIT mode succeeds without size data")
        void tailorVisit_succeedsWithoutSize() {
            CustomOrderRequest req = validRequest();
            req.setSizeMode(SizeMode.TAILOR_VISIT);
            req.setChartSize(null);

            when(referenceGenerator.generate()).thenReturn("EAB-CD-20260501-X7K2");
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomOrder result = service.submit(req, null);
            assertThat(result.getSize().getMode()).isEqualTo(SizeMode.TAILOR_VISIT);
        }

        @Test
        @DisplayName("Requires WhatsApp or phone — not both, but at least one")
        void noContact_rejected() {
            CustomOrderRequest req = validRequest();
            req.setWhatsappNumber(null);
            req.setPhoneNumber(null);

            assertThatThrownBy(() -> service.submit(req, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("WhatsApp number or a phone number");
        }

        @Test
        @DisplayName("NIGERIA delivery requires address")
        void nigeriaDelivery_withoutAddress_rejected() {
            CustomOrderRequest req = validRequest();
            req.setDeliveryMode(DeliveryMode.NIGERIA);
            req.setDeliveryAddress(null);

            assertThatThrownBy(() -> service.submit(req, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Delivery address");
        }

        @Test
        @DisplayName("NIGERIA delivery succeeds with address")
        void nigeriaDelivery_withAddress_succeeds() {
            CustomOrderRequest req = validRequest();
            req.setDeliveryMode(DeliveryMode.NIGERIA);
            req.setDeliveryAddress(Address.builder()
                    .streetAddress("12 Ikoyi Crescent")
                    .city("Lagos")
                    .state("Lagos")
                    .country("Nigeria")
                    .build());

            when(referenceGenerator.generate()).thenReturn("EAB-CD-20260501-X7K2");
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomOrder result = service.submit(req, null);
            assertThat(result.getDelivery().getMode()).isEqualTo(DeliveryMode.NIGERIA);
        }

        @Test
        @DisplayName("Uploads inline data URLs to storage and stores returned URLs")
        void inlineDataUrls_uploaded() {
            CustomOrderRequest req = validRequest();
            req.setReferenceImages(List.of(
                    StyleImageInput.builder().dataUrl("data:image/png;base64,iVBORw0KGgo=").build(),
                    StyleImageInput.builder().url("https://cdn.example/already-uploaded.jpg").build()
            ));

            when(storageService.uploadDataUrl(anyString(), anyString()))
                    .thenReturn(new StorageService.UploadResult(
                            "https://res.cloudinary.com/demo/image/upload/abc.png", "abc"));
            when(referenceGenerator.generate()).thenReturn("EAB-CD-20260501-X7K2");
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomOrder result = service.submit(req, null);

            assertThat(result.getStyle().getReferenceImageUrls()).containsExactly(
                    "https://res.cloudinary.com/demo/image/upload/abc.png",  // dataUrl → uploaded (position 0)
                    "https://cdn.example/already-uploaded.jpg"               // pre-uploaded URL (position 1)
            );
            verify(storageService, times(1)).uploadDataUrl(anyString(), anyString());
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  QUOTE
    // ═════════════════════════════════════════════════════════════════════

    @Nested
    class ApplyQuote {

        @Test
        @DisplayName("Sets quoted amount, computes 50% deposit and balance")
        void valid_computesDepositAndBalance() {
            CustomOrder order = CustomOrder.builder()
                    .referenceNumber("EAB-CD-20260501-X7K2")
                    .categoryId("agbada")
                    .status(CustomOrderStatus.SUBMITTED)
                    .pricing(PricingSpec.builder().build())
                    .build();
            when(repository.findByReferenceNumber("EAB-CD-20260501-X7K2")).thenReturn(Optional.of(order));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            QuoteRequest quote = QuoteRequest.builder()
                    .quotedAmount(new BigDecimal("60000"))
                    .quoteNotes("Premium fabric included")
                    .build();

            CustomOrder result = service.applyQuote("EAB-CD-20260501-X7K2", quote, "admin-1");

            assertThat(result.getStatus()).isEqualTo(CustomOrderStatus.QUOTED);
            assertThat(result.getPricing().getQuotedAmount()).isEqualByComparingTo("60000.00");
            assertThat(result.getPricing().getDepositAmount()).isEqualByComparingTo("30000.00");
            assertThat(result.getPricing().getBalanceAmount()).isEqualByComparingTo("30000.00");
            assertThat(result.getPricing().getQuotedBy()).isEqualTo("admin-1");
            assertThat(result.getPricing().getQuotedAt()).isNotNull();
            verify(eventPublisher).publishEvent(any(CustomOrderQuotedEvent.class));
        }

        @Test
        @DisplayName("Rejects quote on order that's not in SUBMITTED state")
        void notSubmitted_rejected() {
            CustomOrder order = CustomOrder.builder()
                    .referenceNumber("EAB-CD-20260501-X7K2")
                    .categoryId("agbada")
                    .status(CustomOrderStatus.QUOTED)  // already quoted
                    .build();
            when(repository.findByReferenceNumber(any())).thenReturn(Optional.of(order));

            QuoteRequest quote = QuoteRequest.builder()
                    .quotedAmount(new BigDecimal("60000")).build();

            assertThatThrownBy(() -> service.applyQuote("EAB-CD-20260501-X7K2", quote, "admin-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SUBMITTED");
        }

        @Test
        @DisplayName("Throws when reference number does not exist")
        void unknownReference_throws() {
            when(repository.findByReferenceNumber(any())).thenReturn(Optional.empty());
            QuoteRequest quote = QuoteRequest.builder()
                    .quotedAmount(new BigDecimal("60000")).build();

            assertThatThrownBy(() -> service.applyQuote("BAD-REF", quote, "admin-1"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Rounds odd amounts correctly (₦60001 → ₦30000.50 deposit)")
        void oddAmount_roundsCorrectly() {
            CustomOrder order = CustomOrder.builder()
                    .referenceNumber("EAB-CD-20260501-X7K2")
                    .categoryId("agbada")
                    .status(CustomOrderStatus.SUBMITTED)
                    .pricing(PricingSpec.builder().build())
                    .build();
            when(repository.findByReferenceNumber(any())).thenReturn(Optional.of(order));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            QuoteRequest quote = QuoteRequest.builder()
                    .quotedAmount(new BigDecimal("60001")).build();
            CustomOrder result = service.applyQuote("EAB-CD-20260501-X7K2", quote, "admin-1");

            assertThat(result.getPricing().getQuotedAmount()).isEqualByComparingTo("60001.00");
            // 60001 / 2 = 30000.50
            assertThat(result.getPricing().getDepositAmount()).isEqualByComparingTo("30000.50");
            assertThat(result.getPricing().getBalanceAmount()).isEqualByComparingTo("30000.50");
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  STATUS TRANSITIONS
    // ═════════════════════════════════════════════════════════════════════

    @Nested
    class StatusTransitions {

        @Test
        @DisplayName("DEPOSIT_PAID → IN_PRODUCTION is allowed")
        void depositPaid_to_inProduction_allowed() {
            CustomOrder order = CustomOrder.builder()
                    .referenceNumber("R1")
                    .status(CustomOrderStatus.DEPOSIT_PAID)
                    .build();
            when(repository.findByReferenceNumber("R1")).thenReturn(Optional.of(order));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomOrder result = service.updateStatus("R1", CustomOrderStatus.IN_PRODUCTION,
                    "Cutting started", "admin-1");

            assertThat(result.getStatus()).isEqualTo(CustomOrderStatus.IN_PRODUCTION);
            assertThat(result.getStatusHistory()).hasSize(1);
        }

        @Test
        @DisplayName("SUBMITTED → IN_PRODUCTION is rejected (must quote first)")
        void submitted_to_inProduction_rejected() {
            CustomOrder order = CustomOrder.builder()
                    .referenceNumber("R1")
                    .status(CustomOrderStatus.SUBMITTED)
                    .build();
            when(repository.findByReferenceNumber("R1")).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.updateStatus("R1", CustomOrderStatus.IN_PRODUCTION,
                    "skip ahead", "admin-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from SUBMITTED to IN_PRODUCTION");
        }

        @Test
        @DisplayName("DELIVERED is terminal except for COMPLETED")
        void delivered_canOnlyGoToCompleted() {
            CustomOrder order = CustomOrder.builder()
                    .referenceNumber("R1")
                    .status(CustomOrderStatus.DELIVERED)
                    .build();
            when(repository.findByReferenceNumber("R1")).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.updateStatus("R1", CustomOrderStatus.CANCELLED,
                    "too late", "admin-1"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("COMPLETED is fully terminal")
        void completed_isTerminal() {
            assertThat(service.nextAllowed(CustomOrderStatus.COMPLETED)).isEmpty();
            assertThat(service.nextAllowed(CustomOrderStatus.REJECTED)).isEmpty();
            assertThat(service.nextAllowed(CustomOrderStatus.CANCELLED)).isEmpty();
        }

        @Test
        @DisplayName("nextAllowed returns the right transitions for each status")
        void nextAllowed_returnsCorrectSets() {
            assertThat(service.nextAllowed(CustomOrderStatus.SUBMITTED))
                    .containsExactlyInAnyOrder(
                            CustomOrderStatus.QUOTED,
                            CustomOrderStatus.REJECTED,
                            CustomOrderStatus.CANCELLED);

            assertThat(service.nextAllowed(CustomOrderStatus.READY))
                    .containsExactlyInAnyOrder(
                            CustomOrderStatus.SHIPPED,
                            CustomOrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("Status history accumulates across transitions")
        void history_accumulates() {
            CustomOrder order = CustomOrder.builder()
                    .referenceNumber("R1")
                    .status(CustomOrderStatus.READY)
                    .build();
            when(repository.findByReferenceNumber("R1")).thenReturn(Optional.of(order));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.updateStatus("R1", CustomOrderStatus.SHIPPED, "Picked up by GIG", "admin-1");

            ArgumentCaptor<CustomOrder> captor = ArgumentCaptor.forClass(CustomOrder.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getStatusHistory()).hasSize(1);
            assertThat(captor.getValue().getStatusHistory().get(0).getNote())
                    .isEqualTo("Picked up by GIG");
            assertThat(captor.getValue().getStatusHistory().get(0).getActor()).isEqualTo("admin-1");
        }
    }
}