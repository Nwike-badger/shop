package semicolon.africa.waylchub.service.paymentService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import semicolon.africa.waylchub.config.CacheConfig;
import semicolon.africa.waylchub.dto.paymentDto.MonnifyAuthApiResponse;
import semicolon.africa.waylchub.exception.PaymentGatewayException;

import java.util.Base64;

/**
 * Handles Monnify authentication in isolation.
 *
 * WHY A SEPARATE CLASS?
 * Spring's @Cacheable works through a proxy. If getAccessToken() were a private
 * method called from within MonnifyPaymentServiceImpl, the call would bypass the
 * proxy entirely — the cache would never be populated. Extracting it here means
 * authService.getAccessToken() always goes through the proxy. ✅
 */
@Slf4j
@Service
public class MonnifyAuthService {

    private final WebClient webClient;

    @Value("${monnify.api-key}")
    private String apiKey;

    @Value("${monnify.secret-key}")
    private String secretKey;

    @Value("${monnify.base-url}")
    private String baseUrl;

    public MonnifyAuthService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Fetches a Monnify access token, caching the result for 55 minutes.
     * All concurrent callers waiting during the first fetch will get the same
     * cached value — Caffeine handles the thundering-herd problem internally.
     */
    @Cacheable(value = CacheConfig.MONNIFY_TOKEN_CACHE, key = "'global_token'")
    public String getAccessToken() {
        log.info("Fetching fresh Monnify access token (cache miss)");

        String credentials = apiKey + ":" + secretKey;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

        try {
            MonnifyAuthApiResponse response = webClient
                    .post()
                    .uri(baseUrl + "/api/v1/auth/login")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new PaymentGatewayException(
                                            "Monnify auth rejected (4xx): " + body))
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new PaymentGatewayException(
                                            "Monnify server error during auth (5xx): " + body))
                    )
                    .bodyToMono(MonnifyAuthApiResponse.class)
                    .block();

            if (response == null || !response.isRequestSuccessful() || response.getResponseBody() == null) {
                throw new PaymentGatewayException(
                        "Monnify returned an unexpected auth response structure");
            }

            log.info("Monnify access token fetched and cached successfully");
            return response.getResponseBody().getAccessToken();

        } catch (WebClientResponseException e) {
            log.error("Monnify authentication HTTP error. Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentGatewayException(
                    "Failed to authenticate with Monnify: " + e.getMessage(), e);
        }
    }

    /**
     * Removes the cached token. Called when a 401 is returned during payment init,
     * which means the cached token expired earlier than expected.
     * The next call to getAccessToken() will fetch a fresh one.
     */
    @CacheEvict(value = CacheConfig.MONNIFY_TOKEN_CACHE, key = "'global_token'")
    public void evictAccessToken() {
        log.warn("Monnify access token evicted from cache — will re-fetch on next request");
    }
}