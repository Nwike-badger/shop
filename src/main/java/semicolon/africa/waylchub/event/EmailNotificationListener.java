package semicolon.africa.waylchub.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import semicolon.africa.waylchub.event.OrderCancelledEvent;
import semicolon.africa.waylchub.event.OrderPaidEvent;
import semicolon.africa.waylchub.service.emailService.EmailService;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationListener {

    private final EmailService emailService;
    private final TemplateEngine templateEngine; // Injected Thymeleaf Engine

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPaidEvent(OrderPaidEvent event) {
        log.info("Background thread processing receipt for order: {}", event.getOrder().getOrderNumber());

        String customerEmail = event.getOrder().getCustomerEmail();
        String subject = "Payment Receipt - Order " + event.getOrder().getOrderNumber();

        // 1. Create the Thymeleaf Context
        Context context = new Context();

        // 2. Map your dynamic variables
        // Adjust these getters to match how your Order and User entities are actually structured
        String email = event.getOrder().getCustomerEmail();
        String extractedName = email.substring(0, email.indexOf("@"));

        context.setVariable("customerEmail", extractedName); // not sure how to get customer name
        context.setVariable("orderNumber", event.getOrder().getOrderNumber());
        context.setVariable("transactionRef", event.getTransactionReference());

        // Pass the list of order items for the th:each loop
        context.setVariable("items", event.getOrder().getItems());

        // Pass the grand total to be formatted in the HTML
        context.setVariable("grandTotal", event.getOrder().getGrandTotal());

        // 3. Process the template into an HTML string
        // Spring Boot automatically looks in src/main/resources/templates/
        String htmlContent = templateEngine.process("emails/order-receipt", context);

        // 4. Send the email
        emailService.sendHtmlEmail(customerEmail, subject, htmlContent);

        log.info("Receipt email successfully queued/sent for order: {}", event.getOrder().getOrderNumber());
    }

    @Async
    @EventListener
    public void handleOrderCancelledEvent(OrderCancelledEvent event) {
        log.info("Background thread processing cancellation email for order ID: {}", event.getOrderId());

        // Future implementation:
        // Context context = new Context();
        // context.setVariable("orderId", event.getOrderId());
        // String htmlContent = templateEngine.process("emails/order-cancelled", context);
        // emailService.sendHtmlEmail(..., "Order Cancelled", htmlContent);
    }
}