package semicolon.africa.waylchub.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.paymentDto.MonnifyWebhookPayload;
import semicolon.africa.waylchub.dto.paymentDto.PaymentInitRequest;
import semicolon.africa.waylchub.dto.paymentDto.PaymentInitResponse;
import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails;
import semicolon.africa.waylchub.dto.userDTO.UserResponse;
import semicolon.africa.waylchub.exception.PaymentGatewayException;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.order.Order;
import semicolon.africa.waylchub.service.orderService.OrderService;
import semicolon.africa.waylchub.service.paymentService.PaymentGatewayService;
import semicolon.africa.waylchub.service.userService.UserService;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentGatewayService paymentService;
    private final OrderService orderService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @PostMapping("/init/{orderId}")
    public ResponseEntity<?> initPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String orderId) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        Order order;
        try {
            order = orderService.getOrderById(orderId);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found"));
        }

        if (!order.getCustomerId().equals(userDetails.getUserId())) {
            log.warn("Forbidden payment init — userId: {} attempted orderId: {}",
                    userDetails.getUserId(), orderId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You are not authorized to pay for this order"));
        }

        String customerName = resolveCustomerName(order.getCustomerId(), order.getCustomerEmail());

        PaymentInitRequest request = PaymentInitRequest.builder()
                .orderId(order.getId())
                .amount(order.getGrandTotal())
                .customerEmail(order.getCustomerEmail())
                .customerName(customerName)
                .paymentDescription("Payment for Order: " + order.getOrderNumber())
                .build();

        try {
            PaymentInitResponse response = paymentService.initializePayment(request);
            log.info("Payment initialized for order: {} — checkoutUrl present: {}",
                    order.getOrderNumber(), response.getCheckoutUrl() != null);
            return ResponseEntity.ok(response);

        } catch (PaymentGatewayException e) {
            // Catch explicitly — previously fell through to Spring's generic 500 body
            // { "status": 500, "error": "Internal Server Error" }
            // which doesn't match the { "error": "..." } shape the frontend reads.
            // 502 Bad Gateway is the correct HTTP status when an upstream service fails.
            log.error("Monnify payment init failed for order {}: {}", orderId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Payment gateway unavailable. Please try again shortly."));
        }
    }

    /**
     * Monnify webhook — must be in Spring Security permitAll() since Monnify sends no JWT.
     */
    @PostMapping("/webhook/monnify")
    public ResponseEntity<Void> handleMonnifyWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "monnify-signature", required = false) String signature) {

        log.info("Received Monnify webhook event");

        if (signature == null || !paymentService.verifyWebhookSignature(rawPayload, signature)) {
            log.warn("Rejected Monnify webhook — invalid or missing signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            MonnifyWebhookPayload payload = objectMapper.readValue(rawPayload, MonnifyWebhookPayload.class);

            if ("SUCCESSFUL_TRANSACTION".equals(payload.getEventType())) {
                MonnifyWebhookPayload.EventData eventData = payload.getEventData();
                if ("PAID".equals(eventData.getPaymentStatus())) {
                    log.info("Processing successful payment — orderId: {}, ref: {}, method: {}",
                            eventData.getPaymentReference(),
                            eventData.getTransactionReference(),
                            eventData.getPaymentMethod());

                    orderService.processSuccessfulPayment(
                            eventData.getPaymentReference(),
                            eventData.getTransactionReference(),
                            eventData.getPaymentMethod()
                    );
                }
            }

            return ResponseEntity.ok().build();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse Monnify webhook payload: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Internal error processing Monnify webhook — Monnify will retry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String resolveCustomerName(String customerId, String email) {
        if (customerId == null || customerId.isBlank()) return "Customer";
        try {
            UserResponse user = userService.getCurrentUser(customerId);
            String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
            String last  = user.getLastName()  != null ? user.getLastName().trim()  : "";
            if (!first.isBlank() && !last.isBlank()) return first + " " + last;
            if (!first.isBlank()) return first;
            if (!last.isBlank())  return last;
        } catch (Exception e) {
            log.warn("Could not resolve name for userId: {}. Using fallback.", customerId);
        }
        return "Customer";
    }
}