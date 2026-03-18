package semicolon.africa.waylchub.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.paymentDto.MonnifyWebhookPayload;
import semicolon.africa.waylchub.dto.paymentDto.PaymentInitRequest;
import semicolon.africa.waylchub.dto.paymentDto.PaymentInitResponse;
import semicolon.africa.waylchub.model.order.Order;
import semicolon.africa.waylchub.service.orderService.OrderService;
import semicolon.africa.waylchub.service.paymentService.PaymentGatewayService;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentGatewayService paymentService;
    private final OrderService orderService;
    private final ObjectMapper objectMapper; // Spring Boot automatically provides this

    @PostMapping("/init/{orderId}")
    public ResponseEntity<PaymentInitResponse> initPayment(@PathVariable String orderId) {
        Order order = orderService.getOrderById(orderId);

        PaymentInitRequest request = PaymentInitRequest.builder()
                .orderId(order.getId()) // Your internal order ID becomes the paymentReference
                .amount(order.getGrandTotal())
                .customerEmail(order.getCustomerEmail())
                .customerName("Customer")
                .paymentDescription("Payment for Order: " + order.getOrderNumber())
                .build();

        PaymentInitResponse response = paymentService.initializePayment(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/webhook/monnify")
    public ResponseEntity<Void> handleMonnifyWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "monnify-signature", required = false) String signature) {

        log.info("Received Monnify Webhook");

        // 1. Security Check
        if (signature == null || !paymentService.verifyWebhookSignature(rawPayload, signature)) {
            log.error("Invalid or missing Monnify Webhook Signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 2. Parse Payload securely using the DTO
            MonnifyWebhookPayload payload = objectMapper.readValue(rawPayload, MonnifyWebhookPayload.class);

            // 3. Process the Event
            if ("SUCCESSFUL_TRANSACTION".equals(payload.getEventType())) {
                MonnifyWebhookPayload.EventData eventData = payload.getEventData();

                if ("PAID".equals(eventData.getPaymentStatus())) {
                    String orderId = eventData.getPaymentReference();
                    String transactionRef = eventData.getTransactionReference();

                    // Call the new transactional method in OrderService
                    orderService.processSuccessfulPayment(orderId, transactionRef);
                }
            }

            // Always return 200 OK so Monnify knows you received it, even if you ignore the event type
            return ResponseEntity.ok().build();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse Monnify webhook payload", e);
            // Return 400 Bad Request so Monnify might retry, or 200 if you want them to stop
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error processing Monnify webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}