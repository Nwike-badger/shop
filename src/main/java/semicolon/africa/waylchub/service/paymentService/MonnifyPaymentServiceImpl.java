package semicolon.africa.waylchub.service.paymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import semicolon.africa.waylchub.dto.paymentDto.PaymentInitRequest;
import semicolon.africa.waylchub.dto.paymentDto.PaymentInitResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonnifyPaymentServiceImpl implements PaymentGatewayService {

    @Value("${monnify.api-key}")
    private String apiKey;

    @Value("${monnify.secret-key}")
    private String secretKey;

    @Value("${monnify.contract-code}")
    private String contractCode;

    @Value("${monnify.base-url}")
    private String baseUrl;

    private final WebClient.Builder webClientBuilder;

    private String getAccessToken() {
        String authString = apiKey + ":" + secretKey;
        String encodedAuth = Base64.getEncoder().encodeToString(authString.getBytes());

        Map response = webClientBuilder.build()
                .post()
                .uri(baseUrl + "/api/v1/auth/login")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        Map<String, Object> responseBody = (Map<String, Object>) response.get("responseBody");
        return (String) responseBody.get("accessToken");
    }

    @Override
    public PaymentInitResponse initializePayment(PaymentInitRequest request) {
        String accessToken = getAccessToken();

        Map<String, Object> payload = Map.of(
                "amount", request.getAmount(),
                "customerName", request.getCustomerName(),
                "customerEmail", request.getCustomerEmail(),
                "paymentReference", request.getOrderId(), // Crucial: Tie Monnify's ref to your Order ID
                "paymentDescription", request.getPaymentDescription(),
                "currencyCode", "NGN",
                "contractCode", contractCode,
                "paymentMethods", new String[]{"CARD", "ACCOUNT_TRANSFER", "USSD"}
        );

        Map response = webClientBuilder.build()
                .post()
                .uri(baseUrl + "/api/v1/merchant/transactions/init-payment")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        Map<String, Object> responseBody = (Map<String, Object>) response.get("responseBody");

        return PaymentInitResponse.builder()
                .checkoutUrl((String) responseBody.get("checkoutUrl"))
                .transactionReference((String) responseBody.get("transactionReference"))
                .paymentReference((String) responseBody.get("paymentReference"))
                .build();
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            Mac sha512Hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            sha512Hmac.init(keySpec);

            byte[] macData = sha512Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder computedHash = new StringBuilder();

            for (byte b : macData) {
                computedHash.append(String.format("%02x", b));
            }

            return computedHash.toString().equals(signature);
        } catch (Exception e) {
            log.error("Error verifying Monnify signature", e);
            return false;
        }
    }
}