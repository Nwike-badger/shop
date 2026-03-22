//package semicolon.africa.waylchub.event;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.thymeleaf.TemplateEngine;
//import org.thymeleaf.context.Context;
//import semicolon.africa.waylchub.dto.userDTO.UserResponse;
//import semicolon.africa.waylchub.exception.UserNotFoundException;
//import semicolon.africa.waylchub.model.order.Order;
//import semicolon.africa.waylchub.model.order.OrderItem;
//import semicolon.africa.waylchub.service.emailService.EmailService;
//import semicolon.africa.waylchub.service.userService.UserService;
//
//import java.math.BigDecimal;
//import java.util.List;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatCode;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//@DisplayName("EmailNotificationListener Tests")
//class EmailNotificationListenerTest {
//
//    @Mock EmailService emailService;
//    @Mock TemplateEngine templateEngine;
//    @Mock UserService userService;
//
//    @InjectMocks
//    EmailNotificationListener listener;
//
//    private Order mockOrder;
//    private OrderPaidEvent mockEvent;
//
//    @BeforeEach
//    void setUp() {
//        OrderItem item = OrderItem.builder()
//                .productName("Aba Leather Bag")
//                .quantity(2)
//                .unitPrice(new BigDecimal("12500.00"))
//                .subTotal(new BigDecimal("25000.00"))
//                .build();
//
//        mockOrder = Order.builder()
//                .orderNumber("ORD-20260101-ABCD")
//                .customerId("user-id-001")
//                .customerEmail("whisper2ikev@gmail.com")
//                .items(List.of(item))
//                .grandTotal(new BigDecimal("27000.00"))
//                .build();
//
//        mockEvent = new OrderPaidEvent(mockOrder, "MNFY_TXN_REF_001");
//
//        // lenient() here because shouldSwallowTemplateEngineException overrides this
//        // stub with thenThrow — making the setUp stub unused in that one test.
//        // lenient() tells Mockito "this is a default, not a contract" and suppresses
//        // UnnecessaryStubbingException without disabling strict stubbing elsewhere.
//        lenient().when(templateEngine.process(anyString(), any(Context.class)))
//                .thenReturn("<html>Receipt</html>");
//    }
//
//    // ── Name resolution ──────────────────────────────────────────────────────
//
//    @Test
//    @DisplayName("Should use firstName + lastName from UserService — not the email address")
//    void shouldUseRealNameFromUserService() {
//        when(userService.getCurrentUser("user-id-001")).thenReturn(
//                UserResponse.builder()
//                        .id("user-id-001")
//                        .firstName("Kevin")
//                        .lastName("Obi")
//                        .email("whisper2ikev@gmail.com")
//                        .build()
//        );
//
//        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
//        listener.handleOrderPaidEvent(mockEvent);
//
//        verify(templateEngine).process(eq("emails/order-receipt"), contextCaptor.capture());
//
//
//        assertThat(contextCaptor.getValue().getVariable("customerName")).isEqualTo("Kevin Obi");
//    }
//
//    @Test
//    @DisplayName("Should use only firstName if lastName is blank")
//    void shouldUseOnlyFirstNameWhenLastNameIsBlank() {
//        when(userService.getCurrentUser("user-id-001")).thenReturn(
//                UserResponse.builder().firstName("Kevin").lastName("").build()
//        );
//
//        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
//        listener.handleOrderPaidEvent(mockEvent);
//        verify(templateEngine).process(anyString(), captor.capture());
//
//        assertThat(captor.getValue().getVariable("customerName")).isEqualTo("Kevin");
//    }
//
//    @Test
//    @DisplayName("Should fall back to 'Customer' when UserService throws UserNotFoundException")
//    void shouldFallBackWhenUserNotFound() {
//        when(userService.getCurrentUser("user-id-001"))
//                .thenThrow(new UserNotFoundException("User not found"));
//
//        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
//        listener.handleOrderPaidEvent(mockEvent);
//        verify(templateEngine).process(anyString(), captor.capture());
//
//        assertThat(captor.getValue().getVariable("customerName")).isEqualTo("Customer");
//    }
//
//    @Test
//    @DisplayName("Should fall back to 'Customer' when user has no firstName or lastName stored")
//    void shouldFallBackWhenUserHasNoName() {
//        when(userService.getCurrentUser("user-id-001")).thenReturn(
//                UserResponse.builder().firstName(null).lastName(null).build()
//        );
//
//        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
//        listener.handleOrderPaidEvent(mockEvent);
//        verify(templateEngine).process(anyString(), captor.capture());
//
//        assertThat(captor.getValue().getVariable("customerName")).isEqualTo("Customer");
//    }
//
//    @Test
//    @DisplayName("Should fall back to 'Customer' when customerId is null (guest checkout)")
//    void shouldFallBackForGuestCheckoutWithNullCustomerId() {
//        Order guestOrder = Order.builder()
//                .orderNumber("ORD-GUEST-001")
//                .customerId(null)        // guest — no account
//                .customerEmail("guest@example.com")
//                .items(List.of())
//                .grandTotal(BigDecimal.ZERO)
//                .build();
//
//        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
//        listener.handleOrderPaidEvent(new OrderPaidEvent(guestOrder, "TXN-GUEST"));
//        verify(templateEngine).process(anyString(), captor.capture());
//
//        assertThat(captor.getValue().getVariable("customerName")).isEqualTo("Customer");
//        // UserService must NOT be called — there's no ID to look up
//        verify(userService, never()).getCurrentUser(any());
//    }
//
//    @Test
//    @DisplayName("Should fall back to 'Customer' when UserService throws an unexpected exception")
//    void shouldFallBackOnUnexpectedUserServiceError() {
//        when(userService.getCurrentUser("user-id-001"))
//                .thenThrow(new RuntimeException("MongoDB timeout"));
//
//        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
//        listener.handleOrderPaidEvent(mockEvent);
//        verify(templateEngine).process(anyString(), captor.capture());
//
//        assertThat(captor.getValue().getVariable("customerName")).isEqualTo("Customer");
//    }
//
//    // ── Context variables ────────────────────────────────────────────────────
//
//    @Test
//    @DisplayName("Should pass all required variables to the Thymeleaf context")
//    void shouldPassAllRequiredVariablesToTemplate() {
//        when(userService.getCurrentUser("user-id-001")).thenReturn(
//                UserResponse.builder().firstName("Kevin").lastName("Obi").build()
//        );
//
//        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
//        listener.handleOrderPaidEvent(mockEvent);
//        verify(templateEngine).process(anyString(), captor.capture());
//
//        Context ctx = captor.getValue();
//        assertThat(ctx.getVariable("customerName")).isEqualTo("Kevin Obi");
//        assertThat(ctx.getVariable("orderNumber")).isEqualTo("ORD-20260101-ABCD");
//        assertThat(ctx.getVariable("transactionRef")).isEqualTo("MNFY_TXN_REF_001");
//        assertThat(ctx.getVariable("items")).isNotNull();
//        assertThat(ctx.getVariable("grandTotal")).isEqualTo(new BigDecimal("27000.00"));
//    }
//
//    @Test
//    @DisplayName("Should send email to correct address with correct subject")
//    void shouldSendEmailToCorrectAddressWithCorrectSubject() {
//        when(userService.getCurrentUser(any())).thenReturn(
//                UserResponse.builder().firstName("Kevin").lastName("Obi").build()
//        );
//
//        listener.handleOrderPaidEvent(mockEvent);
//
//        verify(emailService).sendHtmlEmail(
//                eq("whisper2ikev@gmail.com"),
//                eq("Payment Receipt - Order ORD-20260101-ABCD"),
//                eq("<html>Receipt</html>")
//        );
//    }
//
//    // ── Fault tolerance ──────────────────────────────────────────────────────
//
//    @Test
//    @DisplayName("Should not throw if EmailService fails — email errors must be swallowed")
//    void shouldSwallowEmailServiceException() {
//        when(userService.getCurrentUser(any())).thenReturn(
//                UserResponse.builder().firstName("Kevin").lastName("Obi").build()
//        );
//        doThrow(new RuntimeException("Resend API down"))
//                .when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());
//
//        assertThatCode(() -> listener.handleOrderPaidEvent(mockEvent))
//                .doesNotThrowAnyException();
//    }
//
//    @Test
//    @DisplayName("Should not throw if TemplateEngine fails")
//    void shouldSwallowTemplateEngineException() {
//        when(userService.getCurrentUser(any())).thenReturn(
//                UserResponse.builder().firstName("Kevin").lastName("Obi").build()
//        );
//        when(templateEngine.process(anyString(), any(Context.class)))
//                .thenThrow(new RuntimeException("Template not found"));
//
//        assertThatCode(() -> listener.handleOrderPaidEvent(mockEvent))
//                .doesNotThrowAnyException();
//    }
//}