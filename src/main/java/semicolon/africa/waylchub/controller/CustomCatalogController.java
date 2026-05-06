package semicolon.africa.waylchub.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.customOrderDto.CustomCategoryResponse;
import semicolon.africa.waylchub.dto.customOrderDto.CustomStyleResponse;
import semicolon.africa.waylchub.model.customOrder.CustomCategory;
import semicolon.africa.waylchub.model.customOrder.CustomStyle;
import semicolon.africa.waylchub.service.customOrderService.CustomCategoryService;
import semicolon.africa.waylchub.service.customOrderService.CustomStyleService;

import java.util.List;

/**
 * Public catalog endpoints — no authentication required.
 * Both endpoints are covered by:
 *   .requestMatchers(HttpMethod.GET, "/api/v1/custom-catalog/**").permitAll()
 * in SecurityConfig.
 *
 * GET /api/v1/custom-catalog/categories       → landing grid (active only, no styles)
 * GET /api/v1/custom-catalog/categories/{slug} → wizard (one category + active styles)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/custom-catalog")
@RequiredArgsConstructor
public class CustomCatalogController {

    private final CustomCategoryService categoryService;
    private final CustomStyleService styleService;

    /**
     * Returns all active categories for the /custom landing grid.
     * Styles are NOT nested here — the landing page only needs the card data.
     */
    @GetMapping("/categories")
    public ResponseEntity<List<CustomCategoryResponse>> listCategories() {
        log.debug("[CustomCatalog] Public list requested");
        List<CustomCategoryResponse> body = categoryService.listActive()
                .stream()
                .map(CustomCategoryResponse::from)
                .toList();
        log.debug("[CustomCatalog] Returning {} active categories", body.size());
        return ResponseEntity.ok(body);
    }

    /**
     * Returns one category WITH its active styles nested.
     * Powers the wizard's Style step — one round-trip gives the wizard
     * everything it needs (category metadata + the gallery).
     *
     * Returns inactive categories too so deep-linked URLs can show a
     * "coming soon" message rather than 404.
     */
    @GetMapping("/categories/{slug}")
    public ResponseEntity<CustomCategoryResponse> getCategoryWithStyles(
            @PathVariable String slug) {

        log.debug("[CustomCatalog] Public single-category request: {}", slug);

        CustomCategory category = categoryService.getBySlug(slug);

        List<CustomStyle> styles = styleService.listActiveForCategory(slug);
        List<CustomStyleResponse> styleResponses = styles.stream()
                .map(CustomStyleResponse::from)
                .toList();

        CustomCategoryResponse response = CustomCategoryResponse.from(category);
        response.setSampleStyles(styleResponses);

        log.debug("[CustomCatalog] Category {} returned with {} styles",
                slug, styleResponses.size());
        return ResponseEntity.ok(response);
    }
}