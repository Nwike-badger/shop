package semicolon.africa.waylchub.service.paymentService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import semicolon.africa.waylchub.dto.paymentDto.PaystackVerifyResponse;
import java.math.RoundingMode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import semicolon.africa.waylchub.dto.paymentDto.PaymentVerificationResult;
import semicolon.africa.waylchub.dto.paymentDto.PaystackInitApiResponse;
import semicolon.africa.waylchub.dto.paymentDto.PaymentInitRequest;
import semicolon.africa.waylchub.dto.paymentDto.PaymentInitResponse;
import semicolon.africa.waylchub.exception.PaymentGatewayException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service("paystack")
public class PaystackPaymentServiceImpl implements PaymentGatewayService {

    private final WebClient webClient;

    @Value("${paystack.api-secret-key}")
    private String secretKey;

    @Value("${paystack.callback-url}")
    private String callbackUrl;

    private static final String PAYSTACK_INIT_URL = "https://api.paystack.co/transaction/initialize";
    private static final String PAYSTACK_VERIFY_URL = "https://api.paystack.co/transaction/verify/";

    public PaystackPaymentServiceImpl(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public PaymentInitResponse initializePayment(PaymentInitRequest request) {
        log.info("Paystack: initializing payment — orderId={}, amount={}", request.getOrderId(), request.getAmount());

        // Paystack requires amount in kobo (smallest unit): multiply NGN by 100
        long amountInKobo = request.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        Map<String, Object> payload = new HashMap<>();
        payload.put("email",        request.getCustomerEmail());
        payload.put("amount",       amountInKobo);
        payload.put("reference",    request.getOrderId());   // ← KEY: orderId as reference so callback has it
        payload.put("callback_url", callbackUrl);
        payload.put("metadata", Map.of(
                "order_id",      request.getOrderId(),
                "customer_name", request.getCustomerName() != null ? request.getCustomerName() : "Customer",
                "custom_fields", List.of(
                        Map.of("display_name", "Order ID", "variable_name", "order_id", "value", request.getOrderId())
                )
        ));

        try {
            PaystackInitApiResponse response = webClient
                    .post()
                    .uri(PAYSTACK_INIT_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            resp -> resp.bodyToMono(String.class)
                                    .map(body -> {
                                        log.error("Paystack 4xx: {}", body);
                                        return new PaymentGatewayException("Paystack rejected init: " + body);
                                    })
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class)
                                    .map(body -> {
                                        log.error("Paystack 5xx: {}", body);
                                        return new PaymentGatewayException("Paystack server error: " + body);
                                    })
                    )
                    .bodyToMono(PaystackInitApiResponse.class)
                    .block();

            if (response == null || !response.isStatus() || response.getData() == null) {
                log.error("Paystack: null or failed response: {}", response);
                throw new PaymentGatewayException("Unexpected Paystack response");
            }

            log.info("Paystack init success — reference={}, authUrl={}",
                    response.getData().getReference(), response.getData().getAuthorizationUrl());

            return PaymentInitResponse.builder()
                    .checkoutUrl(response.getData().getAuthorizationUrl()) // ← frontend redirects here
                    .transactionReference(response.getData().getReference())
                    .paymentReference(request.getOrderId())
                    .build();

        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            log.error("Paystack: unexpected error during init", e);
            throw new PaymentGatewayException("Failed to initialize Paystack payment: " + e.getMessage(), e);
        }
    }

    /**
     * Paystack webhook verification: HMAC-SHA512 of raw body using secret key.
     * Header name: x-paystack-signature
     */
    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder computed = new StringBuilder();
            for (byte b : hash) computed.append(String.format("%02x", b));
            boolean valid = computed.toString().equals(signature);
            if (!valid) log.warn("Paystack: webhook signature mismatch");
            return valid;
        } catch (Exception e) {
            log.error("Paystack: signature verification error", e);
            return false;
        }
    }

    /**
     * Server-side verification — the fallback when a webhook is late or missed.
     *
     * Paystack has TWO status fields:
     *   response.status      → whether the API CALL worked (boolean)
     *   response.data.status → the actual TRANSACTION status (string)
     * We care about data.status. Amount comes back in KOBO, so we /100 for NGN.
     *
     * We initialized the transaction with reference = orderId, so we can verify
     * directly by orderId.
     */
    @Override
    public PaymentVerificationResult verifyTransaction(String orderId) {
        try {
            PaystackVerifyResponse response = webClient
                    .get()
                    .uri(PAYSTACK_VERIFY_URL + orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .retrieve()
                    .onStatus(s -> s.value() == 404,
                            r -> reactor.core.publisher.Mono.empty())
                    .bodyToMono(PaystackVerifyResponse.class)
                    .block();

            // API call failed or transaction not found
            if (response == null || !response.isStatus() || response.getData() == null) {
                return PaymentVerificationResult.builder()
                        .status(PaymentVerificationResult.Status.NOT_FOUND)
                        .orderId(orderId)
                        .build();
            }

            PaystackVerifyResponse.Data d = response.getData();
            PaymentVerificationResult.Status status =
                    switch (d.getStatus() == null ? "" : d.getStatus().toLowerCase()) {
                        case "success"                 -> PaymentVerificationResult.Status.SUCCESSFUL;
                        case "ongoing", "pending"      -> PaymentVerificationResult.Status.PENDING;
                        case "failed", "abandoned",
                                "reversed"                -> PaymentVerificationResult.Status.FAILED;
                        default                        -> PaymentVerificationResult.Status.FAILED;
                    };

            // Paystack amount is in kobo → convert to NGN
            BigDecimal amountNgn = d.getAmount() != null
                    ? d.getAmount().divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    : null;

            return PaymentVerificationResult.builder()
                    .status(status)
                    .orderId(orderId)
                    .gatewayReference(d.getReference())   // Paystack reference == our orderId
                    .amount(amountNgn)
                    .currency(d.getCurrency())
                    .paymentMethod(d.getChannel())
                    .build();

        } catch (WebClientResponseException e) {
            log.error("Paystack verify HTTP error for {}: status={} body={}",
                    orderId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentGatewayException("Paystack verify failed", e);
        } catch (Exception e) {
            log.error("Paystack verify unexpected error for {}: {}", orderId, e.getMessage());
            throw new PaymentGatewayException("Paystack verify failed", e);
        }
    }


}