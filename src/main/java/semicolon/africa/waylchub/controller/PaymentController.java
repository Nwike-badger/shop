package semicolon.africa.waylchub.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.paymentDto.*;
import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails;
import semicolon.africa.waylchub.dto.userDTO.UserResponse;
import semicolon.africa.waylchub.exception.PaymentGatewayException;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.order.Order;
import semicolon.africa.waylchub.model.order.OrderStatus;
import semicolon.africa.waylchub.service.orderService.OrderService;
import semicolon.africa.waylchub.service.paymentService.PaymentGatewayService;
import semicolon.africa.waylchub.service.userService.UserService;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    /**
     * Spring auto-populates this with every PaymentGatewayService bean,
     * keyed by its @Service("...") name → "flutterwave", "paystack", "monnify".
     * Add a new gateway by dropping in a new @Service("new") impl — nothing
     * to wire manually here.
     */
    private final Map<String, PaymentGatewayService> gateways;
    private final PaymentGatewayService defaultGateway; // the @Primary bean (Flutterwave)
    private final OrderService orderService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public PaymentController(
            Map<String, PaymentGatewayService> gateways,
            @Qualifier("flutterwave") PaymentGatewayService defaultGateway,
            OrderService orderService,
            UserService userService,
            ObjectMapper objectMapper) {
        this.gateways = gateways;
        this.defaultGateway = defaultGateway;
        this.orderService = orderService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    // ========================================================================
    //  INIT
    // ========================================================================

    @PostMapping("/init/{orderId}")
    public ResponseEntity<?> initPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String orderId,
            @RequestParam(name = "gateway", required = false) String gatewayName,
            @RequestBody(required = false) InitPaymentRequestBody body) {

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

        if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "This order has already been processed or cancelled"));
        }

        PaymentGatewayService selected = resolveGateway(gatewayName);
        String resolvedName = resolveName(selected);
        PaymentInitRequest request = buildInitRequest(order, body);

        try {
            PaymentInitResponse response = selected.initializePayment(request);
            orderService.setPaymentGateway(order.getId(), resolvedName);
            log.info("Payment initialized [{}] for order: {} — checkoutUrl present: {}",
                    resolvedName, order.getOrderNumber(), response.getCheckoutUrl() != null);
            return ResponseEntity.ok(response);
        } catch (PaymentGatewayException e) {
            log.error("Payment init failed on {} for order {}: {}", resolvedName, orderId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Payment gateway unavailable. Please try again shortly."));
        }
    }

    // ========================================================================
    //  RETRY — reuses the same order (no new stock hold)
    // ========================================================================

    @PostMapping("/retry/{orderId}")
    public ResponseEntity<?> retryPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String orderId,
            @RequestParam(name = "gateway", required = false) String gatewayName,
            @RequestBody(required = false) InitPaymentRequestBody body) {

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
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
        }

        if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "This order has already been processed or cancelled"));
        }

        // Priority: explicit ?gateway= → order's saved gateway → @Primary default
        String effectiveGateway = notBlank(gatewayName) ? gatewayName : order.getPaymentGateway();
        PaymentGatewayService selected = resolveGateway(effectiveGateway);
        String resolvedName = resolveName(selected);
        PaymentInitRequest request = buildInitRequest(order, body);

        try {
            PaymentInitResponse response = selected.initializePayment(request);
            // If customer switched gateway on retry, remember the new one
            if (!resolvedName.equalsIgnoreCase(order.getPaymentGateway())) {
                orderService.setPaymentGateway(order.getId(), resolvedName);
            }
            log.info("Payment retry initialized [{}] for order: {}", resolvedName, order.getOrderNumber());
            return ResponseEntity.ok(response);
        } catch (PaymentGatewayException e) {
            log.error("Payment retry failed on {} for order {}: {}", resolvedName, orderId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Payment gateway unavailable. Please try again shortly."));
        }
    }

    // ========================================================================
    //  VERIFY — the fallback path when webhooks are late
    // ========================================================================

    /**
     * Called by the frontend PaymentCallback polling loop.
     *
     * Fast path (99% of the time): webhook already updated the order → return status.
     * Slow path: order still PENDING_PAYMENT → ask the gateway of record directly.
     *   If it says successful, run the same processSuccessfulPayment logic the
     *   webhook would (it's idempotent, so a late webhook is safely ignored).
     *
     * NEVER call this from the redirect URL params — always come back through here.
     */
    @GetMapping("/verify/{orderId}")
    public ResponseEntity<?> verifyPayment(
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
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
        }

        // Fast path
        if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT) {
            return ResponseEntity.ok(Map.of("status", order.getOrderStatus().name()));
        }

        // Slow path — ask the gateway
        String gatewayName = notBlank(order.getPaymentGateway())
                ? order.getPaymentGateway() : "flutterwave";
        PaymentGatewayService gw = gateways.getOrDefault(gatewayName, defaultGateway);

        try {
            PaymentVerificationResult result = gw.verifyTransaction(orderId);
            if (result.getStatus() == PaymentVerificationResult.Status.SUCCESSFUL) {
                orderService.processSuccessfulPayment(
                        orderId,
                        result.getGatewayReference() != null ? result.getGatewayReference() : orderId,
                        notBlank(result.getPaymentMethod()) ? result.getPaymentMethod() : gatewayName);
                Order refreshed = orderService.getOrderById(orderId);
                return ResponseEntity.ok(Map.of("status", refreshed.getOrderStatus().name()));
            }
            // Still pending or failed at the gateway — frontend will keep polling / show failure
            return ResponseEntity.ok(Map.of("status", order.getOrderStatus().name()));
        } catch (Exception e) {
            log.warn("Verify fallback failed for order {} on {}: {}", orderId, gatewayName, e.getMessage());
            return ResponseEntity.ok(Map.of("status", order.getOrderStatus().name()));
        }
    }

    // ========================================================================
    //  WEBHOOKS — one per gateway, each in Spring Security permitAll()
    // ========================================================================

    @PostMapping("/webhook/flutterwave")
    public ResponseEntity<Void> handleFlutterwaveWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "verif-hash", required = false) String signature) {

        log.info("Received Flutterwave webhook event");

        PaymentGatewayService flw = gateways.get("flutterwave");
        if (signature == null || flw == null || !flw.verifyWebhookSignature(rawPayload, signature)) {
            log.warn("Rejected Flutterwave webhook — invalid or missing verif-hash");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            FlutterwaveWebhookPayload payload = objectMapper.readValue(rawPayload, FlutterwaveWebhookPayload.class);
            if ("charge.completed".equals(payload.getEvent())) {
                FlutterwaveWebhookPayload.EventData data = payload.getData();
                if ("successful".equals(data.getStatus())) {
                    log.info("Flutterwave: successful payment — orderId={}, flwRef={}, method={}",
                            data.getTxRef(), data.getFlwRef(), data.getPaymentType());
                    orderService.processSuccessfulPayment(
                            data.getTxRef(),
                            data.getFlwRef(),
                            notBlank(data.getPaymentType()) ? data.getPaymentType() : "flutterwave"
                    );
                }
            }
            return ResponseEntity.ok().build();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Flutterwave webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error processing Flutterwave webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/webhook/paystack")
    public ResponseEntity<Void> handlePaystackWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "x-paystack-signature", required = false) String signature) {

        log.info("Received Paystack webhook event");

        PaymentGatewayService paystack = gateways.get("paystack");
        if (signature == null || paystack == null || !paystack.verifyWebhookSignature(rawPayload, signature)) {
            log.warn("Rejected Paystack webhook — invalid or missing signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            PaystackWebhookPayload payload = objectMapper.readValue(rawPayload, PaystackWebhookPayload.class);
            if ("charge.success".equals(payload.getEvent())) {
                PaystackWebhookPayload.EventData data = payload.getData();
                if ("success".equals(data.getStatus())) {
                    log.info("Paystack: successful payment — orderId={}, channel={}",
                            data.getReference(), data.getChannel());
                    orderService.processSuccessfulPayment(
                            data.getReference(),
                            data.getReference(),
                            data.getChannel()
                    );
                }
            }
            return ResponseEntity.ok().build();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Paystack webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error processing Paystack webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/webhook/monnify")
    public ResponseEntity<Void> handleMonnifyWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "monnify-signature", required = false) String signature) {

        log.info("Received Monnify webhook event");

        PaymentGatewayService monnify = gateways.get("monnify");
        if (signature == null || monnify == null || !monnify.verifyWebhookSignature(rawPayload, signature)) {
            log.warn("Rejected Monnify webhook — invalid or missing signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            MonnifyWebhookPayload payload = objectMapper.readValue(rawPayload, MonnifyWebhookPayload.class);
            if ("SUCCESSFUL_TRANSACTION".equals(payload.getEventType())) {
                MonnifyWebhookPayload.EventData eventData = payload.getEventData();
                if ("PAID".equals(eventData.getPaymentStatus())) {
                    log.info("Monnify: successful payment — orderId: {}, ref: {}, method: {}",
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

    // ========================================================================
    //  HELPERS
    // ========================================================================

    private PaymentInitRequest buildInitRequest(Order order, InitPaymentRequestBody body) {
        String customerName = resolveCustomerName(order.getCustomerId(), order.getCustomerEmail());
        return PaymentInitRequest.builder()
                .orderId(order.getId())
                .amount(order.getGrandTotal())
                .customerEmail(order.getCustomerEmail())
                .customerName(customerName)
                .paymentDescription("Payment for Order: " + order.getOrderNumber())
                .returnUrl(body != null ? body.getReturnUrl() : null)
                .build();
    }

    private PaymentGatewayService resolveGateway(String requested) {
        if (!notBlank(requested)) return defaultGateway;
        PaymentGatewayService svc = gateways.get(requested.toLowerCase());
        if (svc == null) {
            log.warn("Unknown gateway '{}' requested — falling back to default", requested);
            return defaultGateway;
        }
        return svc;
    }

    private String resolveName(PaymentGatewayService svc) {
        return gateways.entrySet().stream()
                .filter(e -> e.getValue() == svc)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("flutterwave");
    }

    private String resolveCustomerName(String customerId, String email) {
        if (!notBlank(customerId)) return "Customer";
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

    private boolean notBlank(String s) { return s != null && !s.isBlank(); }
}