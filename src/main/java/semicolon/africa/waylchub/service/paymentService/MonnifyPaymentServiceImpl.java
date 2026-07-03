package semicolon.africa.waylchub.service.paymentService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import semicolon.africa.waylchub.dto.paymentDto.*;
import semicolon.africa.waylchub.exception.PaymentGatewayException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service("monnify")
public class MonnifyPaymentServiceImpl implements PaymentGatewayService {

    private final WebClient webClient;
    private final MonnifyAuthService authService;

    @Value("${monnify.secret-key}")
    private String secretKey;

    @Value("${monnify.contract-code}")
    private String contractCode;

    @Value("${monnify.base-url}")
    private String baseUrl;

    /**
     * The URL Monnify redirects the user to after payment completes.
     * Monnify appends: ?paymentReference=ORDER_ID&paymentStatus=PAID&transactionReference=MNFY|...
     *
     * Set in application.properties:
     *   monnify.redirect-url=http://localhost:5173/payment/callback   (dev)
     *   monnify.redirect-url=https://yourdomain.com/payment/callback  (prod)
     *
     * This is NOT a Monnify dashboard setting — it must be sent in every transaction payload.
     */
    @Value("${monnify.redirect-url}")
    private String redirectUrl;

    public MonnifyPaymentServiceImpl(WebClient.Builder webClientBuilder,
                                     MonnifyAuthService authService) {
        this.webClient   = webClientBuilder.build();
        this.authService = authService;
    }

    @Override
    public PaymentInitResponse initializePayment(PaymentInitRequest request) {
        log.info("══════════════════════════════════════════════");
        log.info("PAYMENT INIT [1/4] Starting — orderId={}", request.getOrderId());
        log.info("PAYMENT INIT [1/4] amount={} | email={} | contractCode={} | baseUrl={} | redirectUrl={}",
                request.getAmount(), request.getCustomerEmail(), contractCode, baseUrl, redirectUrl);

        String accessToken;
        try {
            accessToken = authService.getAccessToken();
            log.info("PAYMENT INIT [2/4] Auth token acquired (tokenLength={})",
                    accessToken != null ? accessToken.length() : 0);
        } catch (PaymentGatewayException e) {
            log.error("PAYMENT INIT [2/4] FAILED — Could not get Monnify token. Error: {}", e.getMessage());
            throw e;
        }

        // Use HashMap (not Map.of) because Map.of does not allow null values
        // and we need to add redirectUrl which could theoretically be misconfigured.
        Map<String, Object> payload = new HashMap<>();
        payload.put("amount",             request.getAmount());
        payload.put("customerName",       request.getCustomerName());
        payload.put("customerEmail",      request.getCustomerEmail());
        payload.put("paymentReference",   request.getOrderId());
        payload.put("paymentDescription", request.getPaymentDescription());
        payload.put("currencyCode",       "NGN");
        payload.put("contractCode",       contractCode);
        payload.put("paymentMethods",     new String[]{"CARD", "ACCOUNT_TRANSFER", "USSD"});
        // ✅ KEY FIX: Tells Monnify where to redirect after payment
        // Without this, Monnify stays on their own page indefinitely after payment
        // Mobile sends its own returnUrl (custom scheme like exploreabamobile://payment-callback).
// Web omits it and falls back to the configured default.
        String effectiveRedirectUrl = (request.getReturnUrl() != null && !request.getReturnUrl().isBlank())
                ? request.getReturnUrl()
                : redirectUrl;
        log.info("PAYMENT INIT [1/4] amount={} | email={} | contractCode={} | baseUrl={}",
                request.getAmount(), request.getCustomerEmail(), contractCode, baseUrl);
        payload.put("redirectUrl", effectiveRedirectUrl);

        log.info("PAYMENT INIT [3/4] Calling Monnify init-transaction endpoint...");

        try {
            MonnifyInitApiResponse response = webClient
                    .post()
                    .uri(baseUrl + "/api/v1/merchant/transactions/init-transaction")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(
                            status -> status.value() == 401,
                            clientResponse -> {
                                authService.evictAccessToken();
                                return clientResponse.bodyToMono(String.class)
                                        .map(body -> {
                                            log.error("PAYMENT INIT — 401 from Monnify. Token evicted. Body: {}", body);
                                            return new PaymentGatewayException(
                                                    "Monnify auth failed (401) — token evicted. Please retry.");
                                        });
                            }
                    )
                    .onStatus(
                            status -> status.is4xxClientError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> {
                                        log.error("PAYMENT INIT — 4xx from Monnify. Body: {}", body);
                                        return new PaymentGatewayException(
                                                "Monnify rejected payment init (4xx): " + body);
                                    })
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> {
                                        log.error("PAYMENT INIT — 5xx from Monnify. Body: {}", body);
                                        return new PaymentGatewayException(
                                                "Monnify server error (5xx): " + body);
                                    })
                    )
                    .bodyToMono(MonnifyInitApiResponse.class)
                    .block();

            if (response == null || !response.isRequestSuccessful() || response.getResponseBody() == null) {
                log.error("PAYMENT INIT — Monnify 200 but requestSuccessful=false. Response: {}", response);
                throw new PaymentGatewayException("Monnify returned unexpected payment init response");
            }

            MonnifyInitApiResponse.InitBody body = response.getResponseBody();
            log.info("PAYMENT INIT [4/4] SUCCESS — transactionRef={}", body.getTransactionReference());
            log.info("PAYMENT INIT [4/4] checkoutUrl={}", body.getCheckoutUrl());
            log.info("══════════════════════════════════════════════");

            return PaymentInitResponse.builder()
                    .checkoutUrl(body.getCheckoutUrl())
                    .transactionReference(body.getTransactionReference())
                    .paymentReference(body.getPaymentReference())
                    .build();

        } catch (WebClientResponseException e) {
            log.error("PAYMENT INIT — WebClient error. status={} body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentGatewayException(
                    "Failed to initialize payment with Monnify: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            Mac sha512Hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            sha512Hmac.init(keySpec);

            byte[] macData = sha512Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder computedHash = new StringBuilder();
            for (byte b : macData) computedHash.append(String.format("%02x", b));

            boolean isValid = computedHash.toString().equals(signature);
            if (!isValid) {
                log.warn("WEBHOOK — Signature mismatch. Possible tampered payload or wrong secret key.");
            }
            return isValid;
        } catch (Exception e) {
            log.error("WEBHOOK — Error during signature verification", e);
            return false;
        }
    }

    /**
     * Server-side verification — the fallback when a webhook is late or missed.
     *
     * Monnify's query endpoint takes our paymentReference (= orderId) and returns
     * the transaction under responseBody. Uses the same cached auth token as init.
     * Unlike Paystack, Monnify amounts come back in major units (NGN), not kobo.
     *
     * The paymentReference is URL-encoded because order IDs are safe, but Monnify
     * refs sometimes contain characters that need encoding — defensive.
     */
    @Override
    public PaymentVerificationResult verifyTransaction(String orderId) {
        String accessToken;
        try {
            accessToken = authService.getAccessToken();
        } catch (PaymentGatewayException e) {
            log.error("Monnify verify — could not get token for {}: {}", orderId, e.getMessage());
            throw e;
        }

        try {
            MonnifyQueryResponse response = webClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host(baseUrl.replace("https://", "").replace("http://", ""))
                            .path("/api/v1/merchant/transactions/query")
                            .queryParam("paymentReference", orderId)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(status -> status.value() == 401, clientResponse -> {
                        authService.evictAccessToken();
                        return clientResponse.bodyToMono(String.class)
                                .map(body -> new PaymentGatewayException(
                                        "Monnify verify auth failed (401) — token evicted"));
                    })
                    .onStatus(status -> status.value() == 404,
                            clientResponse -> reactor.core.publisher.Mono.empty())
                    .bodyToMono(MonnifyQueryResponse.class)
                    .block();

            if (response == null || !response.isRequestSuccessful() || response.getResponseBody() == null) {
                return PaymentVerificationResult.builder()
                        .status(PaymentVerificationResult.Status.NOT_FOUND)
                        .orderId(orderId)
                        .build();
            }

            MonnifyQueryResponse.ResponseBody b = response.getResponseBody();
            PaymentVerificationResult.Status status =
                    switch (b.getPaymentStatus() == null ? "" : b.getPaymentStatus().toUpperCase()) {
                        case "PAID"                      -> PaymentVerificationResult.Status.SUCCESSFUL;
                        case "PENDING"                   -> PaymentVerificationResult.Status.PENDING;
                        case "FAILED", "EXPIRED",
                                "CANCELLED"                 -> PaymentVerificationResult.Status.FAILED;
                        default                          -> PaymentVerificationResult.Status.FAILED;
                    };

            return PaymentVerificationResult.builder()
                    .status(status)
                    .orderId(orderId)
                    .gatewayReference(b.getTransactionReference())
                    .amount(b.getAmountPaid())          // already NGN, no /100
                    .currency(b.getCurrencyCode())
                    .paymentMethod(b.getPaymentMethod())
                    .build();

        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            log.error("Monnify verify unexpected error for {}: {}", orderId, e.getMessage());
            throw new PaymentGatewayException("Monnify verify failed", e);
        }
    }
}