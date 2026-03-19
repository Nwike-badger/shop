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
import semicolon.africa.waylchub.dto.userDTO.UserResponse;
import semicolon.africa.waylchub.exception.UserNotFoundException;
import semicolon.africa.waylchub.service.emailService.EmailService;
import semicolon.africa.waylchub.service.userService.UserService;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationListener {

    private final EmailService emailService;
    private final TemplateEngine templateEngine;
    private final UserService userService; // ✅ Injected for proper name resolution

    /**
     * Sends a payment receipt email after the order transaction commits.
     *
     * WHY THIS IS FAST:
     * This entire method runs on the "AsyncEvent-" thread pool, AFTER the HTTP
     * response has already been returned to the user. The user DB lookup here
     * costs zero milliseconds on API response time.
     *
     * WHY WE LOOK UP BY customerId AND NOT email:
     * Emails like "whisper2ikev@gmail.com" are meaningless as display names.
     * The User document already has firstName + lastName — we just fetch it.
     * If the lookup fails for any reason, we fall back gracefully.
     */
    @Async("asyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPaidEvent(OrderPaidEvent event) {
        String orderNumber = event.getOrder().getOrderNumber();
        log.info("Processing receipt email for order: {}", orderNumber);

        try {
            String customerEmail = event.getOrder().getCustomerEmail();
            String customerName  = resolveCustomerName(event.getOrder().getCustomerId(), customerEmail);

            Context context = new Context();
            context.setVariable("customerName",   customerName);
            context.setVariable("orderNumber",    orderNumber);
            context.setVariable("transactionRef", event.getTransactionReference());
            context.setVariable("items",          event.getOrder().getItems());
            context.setVariable("grandTotal",     event.getOrder().getGrandTotal());

            String htmlContent = templateEngine.process("emails/order-receipt", context);
            String subject     = "Payment Receipt - Order " + orderNumber;

            emailService.sendHtmlEmail(customerEmail, subject, htmlContent);
            log.info("Receipt email dispatched for order: {}", orderNumber);

        } catch (Exception e) {
            // Intentionally swallowed — email failure is non-fatal to the order
            log.error("Failed to send receipt email for order {}. Manual follow-up may be needed. Error: {}",
                    orderNumber, e.getMessage(), e);
        }
    }

    @Async("asyncExecutor")
    @EventListener
    public void handleOrderCancelledEvent(OrderCancelledEvent event) {
        log.info("Order cancellation event received for order: {} — email not yet implemented",
                event.getOrderId());
        // TODO: implement order-cancelled email template
    }

    /**
     * Resolves a human-readable display name for the receipt greeting.
     *
     * Strategy (in order of preference):
     *   1. Look up the User by customerId → use "FirstName LastName"
     *   2. If lookup fails (deleted user, guest checkout, etc.) → fall back to "Customer"
     *
     * This runs on a background thread so the latency of one MongoDB findById
     * is completely invisible to the caller.
     *
     * @param customerId the Order's stored customerId (may be null for guest orders)
     * @param email      fallback context for logging only
     */
    private String resolveCustomerName(String customerId, String email) {
        if (customerId == null || customerId.isBlank()) {
            log.warn("Order has no customerId (possible guest checkout) for email: {}. Using fallback.", email);
            return "Customer";
        }

        try {
            UserResponse user = userService.getCurrentUser(customerId);

            String fullName = buildFullName(user.getFirstName(), user.getLastName());

            if (fullName.isBlank()) {
                log.warn("User {} has no firstName/lastName stored. Using fallback.", customerId);
                return "Customer";
            }

            return fullName;

        } catch (UserNotFoundException e) {
            // User was deleted after placing the order — not a crash-worthy event
            log.warn("Could not resolve name for customerId: {}. User may have been deleted. Using fallback.", customerId);
            return "Customer";

        } catch (Exception e) {
            // DB hiccup, deserialization issue, etc. — never let this kill the email
            log.error("Unexpected error resolving customer name for customerId: {}. Using fallback.", customerId, e);
            return "Customer";
        }
    }

    private String buildFullName(String firstName, String lastName) {
        String first = (firstName != null) ? firstName.trim() : "";
        String last  = (lastName  != null) ? lastName.trim()  : "";

        if (!first.isBlank() && !last.isBlank()) return first + " " + last;
        if (!first.isBlank()) return first;
        if (!last.isBlank())  return last;
        return "";
    }
}