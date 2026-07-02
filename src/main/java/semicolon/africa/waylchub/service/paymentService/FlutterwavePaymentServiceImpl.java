package semicolon.africa.waylchub.service.paymentService;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import semicolon.africa.waylchub.dto.paymentDto.*;
import semicolon.africa.waylchub.exception.PaymentGatewayException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Primary
@Service("flutterwave")
public class FlutterwavePaymentServiceImpl implements PaymentGatewayService {

    private final WebClient webClient;

    @Value("${flutterwave.secret-key}")
    private String secretKey;

    @Value("${flutterwave.secret-hash}")
    private String secretHash;

    @Value("${flutterwave.callback-url}")
    private String callbackUrl;

    @Value("${flutterwave.base-url:https://api.flutterwave.com}")
    private String baseUrl;

    private static final String INIT_PATH = "/v3/payments";
    private static final String VERIFY_BY_REF_PATH = "/v3/transactions/verify_by_reference?tx_ref=";

    public FlutterwavePaymentServiceImpl(WebClient.Builder webClientBuilder) {
        // ✅ CHANGE 1: real timeouts. Without these, a Flutterwave outage stalls checkout indefinitely.
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .responseTimeout(Duration.ofSeconds(15))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(15, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(15, TimeUnit.SECONDS)));

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public PaymentInitResponse initializePayment(PaymentInitRequest request) {
        log.info("Flutterwave init — orderId={} amount={} NGN",
                request.getOrderId(), request.getAmount());

        String effectiveRedirectUrl = notBlank(request.getReturnUrl()) ? request.getReturnUrl() : callbackUrl;

        Map<String, Object> customer = new HashMap<>();
        customer.put("email", request.getCustomerEmail());
        customer.put("name",  notBlank(request.getCustomerName()) ? request.getCustomerName() : "Customer");

        Map<String, Object> customizations = new LinkedHashMap<>();
        customizations.put("title",       "ExploreAba");
        customizations.put("description", request.getPaymentDescription());

        // ✅ CHANGE 2: meta payload — order_id shows up in Flutterwave dashboard transaction detail.
        // Massive time-saver for support / dispute resolution.
        Map<String, Object> meta = new HashMap<>();
        meta.put("order_id", request.getOrderId());
        meta.put("gateway",  "flutterwave");
        meta.put("source",   "web");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tx_ref",         request.getOrderId());
        payload.put("amount",         request.getAmount());
        payload.put("currency",       "NGN");
        payload.put("redirect_url",   effectiveRedirectUrl);
        payload.put("customer",       customer);
        payload.put("customizations", customizations);
        payload.put("meta",           meta);
        // payment_options intentionally omitted — enable/disable from
        // Settings → Preferred Payment Methods on the dashboard.

        try {
            FlutterwaveInitApiResponse response = webClient
                    .post()
                    .uri(baseUrl + INIT_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(s -> s.is4xxClientError(), r -> r.bodyToMono(String.class)
                            .map(body -> {
                                log.error("Flutterwave 4xx: {}", truncate(body));
                                return new PaymentGatewayException("Flutterwave rejected init");
                            }))
                    .onStatus(s -> s.is5xxServerError(), r -> r.bodyToMono(String.class)
                            .map(body -> {
                                log.error("Flutterwave 5xx: {}", truncate(body));
                                return new PaymentGatewayException("Flutterwave server error");
                            }))
                    .bodyToMono(FlutterwaveInitApiResponse.class)
                    // ✅ CHANGE 3: retry only on server errors (5xx / timeouts). Never on 4xx.
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(400))
                            .filter(this::isTransient)
                            .doBeforeRetry(sig -> log.warn("Flutterwave transient error — retrying, attempt {}",
                                    sig.totalRetries() + 1)))
                    .block();

            if (response == null || !response.isSuccess()
                    || response.getData() == null || response.getData().getLink() == null) {
                throw new PaymentGatewayException("Unexpected Flutterwave response");
            }

            // ✅ CHANGE 4: don't log the full checkout URL — it contains a session token.
            log.info("Flutterwave init OK — tx_ref={} (checkout URL received)", request.getOrderId());

            return PaymentInitResponse.builder()
                    .checkoutUrl(response.getData().getLink())
                    .transactionReference(request.getOrderId())
                    .paymentReference(request.getOrderId())
                    .build();

        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            log.error("Flutterwave init unexpected error", e);
            throw new PaymentGatewayException("Failed to initialize Flutterwave payment", e);
        }
    }

    /**
     * ✅ CHANGE 5: Server-side verify — the fallback when the webhook is delayed or lost.
     * Called by the polling endpoint AND by an admin reconciliation job.
     * NEVER trust the redirect URL query params on the frontend; always come back through here.
     */
    @Override
    public PaymentVerificationResult verifyTransaction(String orderId) {
        try {
            FlutterwaveVerifyResponse response = webClient
                    .get()
                    .uri(baseUrl + VERIFY_BY_REF_PATH + orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .retrieve()
                    .onStatus(s -> s.value() == 404, r -> reactor.core.publisher.Mono.empty())
                    .bodyToMono(FlutterwaveVerifyResponse.class)
                    .block();

            if (response == null || response.getData() == null) {
                return PaymentVerificationResult.builder()
                        .status(PaymentVerificationResult.Status.NOT_FOUND)
                        .orderId(orderId)
                        .build();
            }

            FlutterwaveVerifyResponse.Data d = response.getData();
            PaymentVerificationResult.Status status = switch (d.getStatus() == null ? "" : d.getStatus()) {
                case "successful" -> PaymentVerificationResult.Status.SUCCESSFUL;
                case "pending"    -> PaymentVerificationResult.Status.PENDING;
                default           -> PaymentVerificationResult.Status.FAILED;
            };

            return PaymentVerificationResult.builder()
                    .status(status)
                    .orderId(orderId)
                    .gatewayReference(d.getFlwRef())
                    .amount(d.getAmount() != null ? BigDecimal.valueOf(d.getAmount()) : null)
                    .currency(d.getCurrency())
                    .paymentMethod(d.getPaymentType())
                    .build();

        } catch (Exception e) {
            log.error("Flutterwave verify failed for {}: {}", orderId, e.getMessage());
            throw new PaymentGatewayException("Verify failed", e);
        }
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        if (signature == null || secretHash == null || secretHash.isBlank()) {
            log.warn("Flutterwave: missing signature or configured secret hash");
            return false;
        }
        boolean valid = MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                secretHash.getBytes(StandardCharsets.UTF_8));
        if (!valid) log.warn("Flutterwave: verif-hash mismatch");
        return valid;
    }

    // helpers
    private boolean isTransient(Throwable t) {
        return t instanceof PaymentGatewayException
                && t.getMessage() != null
                && t.getMessage().contains("server error");
    }
    private boolean notBlank(String s) { return s != null && !s.isBlank(); }
    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "…[truncated]" : s;
    }
}