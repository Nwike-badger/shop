package semicolon.africa.waylchub.service.paymentService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import semicolon.africa.waylchub.dto.paymentDto.MonnifyInitApiResponse;
import semicolon.africa.waylchub.dto.paymentDto.PaymentInitRequest;
import semicolon.africa.waylchub.dto.paymentDto.PaymentInitResponse;
import semicolon.africa.waylchub.exception.PaymentGatewayException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
public class MonnifyPaymentServiceImpl implements PaymentGatewayService {

    private final WebClient webClient;
    private final MonnifyAuthService authService;

    @Value("${monnify.secret-key}")
    private String secretKey;

    @Value("${monnify.contract-code}")
    private String contractCode;

    @Value("${monnify.base-url}")
    private String baseUrl;

    // Build the WebClient once; it is thread-safe and reusable
    public MonnifyPaymentServiceImpl(WebClient.Builder webClientBuilder,
                                     MonnifyAuthService authService) {
        this.webClient = webClientBuilder.build();
        this.authService = authService;
    }

    @Override
    public PaymentInitResponse initializePayment(PaymentInitRequest request) {
        log.info("Initializing Monnify payment for order: {}", request.getOrderId());

        String accessToken = authService.getAccessToken();

        Map<String, Object> payload = Map.of(
                "amount", request.getAmount(),
                "customerName", request.getCustomerName(),
                "customerEmail", request.getCustomerEmail(),
                "paymentReference", request.getOrderId(), // Ties Monnify's ref to our Order ID
                "paymentDescription", request.getPaymentDescription(),
                "currencyCode", "NGN",
                "contractCode", contractCode,
                "paymentMethods", new String[]{"CARD", "ACCOUNT_TRANSFER", "USSD"}
        );

        try {
            MonnifyInitApiResponse response = webClient
                    .post()
                    .uri(baseUrl + "/api/v1/merchant/transactions/init-payment")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(
                            status -> status.value() == 401,
                            clientResponse -> {
                                // Token expired earlier than expected — evict and let caller retry
                                authService.evictAccessToken();
                                return clientResponse.bodyToMono(String.class)
                                        .map(body -> new PaymentGatewayException(
                                                "Monnify token expired mid-session. Token evicted. Please retry. Body: " + body));
                            }
                    )
                    .onStatus(
                            status -> status.is4xxClientError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new PaymentGatewayException(
                                            "Monnify rejected payment initialization (4xx): " + body))
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new PaymentGatewayException(
                                            "Monnify server error during payment init (5xx): " + body))
                    )
                    .bodyToMono(MonnifyInitApiResponse.class)
                    .block();

            if (response == null || !response.isRequestSuccessful() || response.getResponseBody() == null) {
                throw new PaymentGatewayException(
                        "Monnify returned an unexpected payment init response structure");
            }

            MonnifyInitApiResponse.InitBody body = response.getResponseBody();
            log.info("Payment initialized. Monnify transactionRef: {}", body.getTransactionReference());

            return PaymentInitResponse.builder()
                    .checkoutUrl(body.getCheckoutUrl())
                    .transactionReference(body.getTransactionReference())
                    .paymentReference(body.getPaymentReference())
                    .build();

        } catch (WebClientResponseException e) {
            log.error("Monnify payment init HTTP error. Status: {}, Body: {}",
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
            for (byte b : macData) {
                computedHash.append(String.format("%02x", b));
            }

            boolean isValid = computedHash.toString().equals(signature);
            if (!isValid) {
                log.warn("Monnify webhook signature mismatch. Possible tampering or misconfigured secret.");
            }
            return isValid;

        } catch (Exception e) {
            log.error("Critical error during Monnify webhook signature verification", e);
            return false;
        }
    }
}