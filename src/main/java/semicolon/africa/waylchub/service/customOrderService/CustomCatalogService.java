package semicolon.africa.waylchub.service.customOrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.dto.customOrderDto.CustomCategoryResponse;
import semicolon.africa.waylchub.dto.customOrderDto.CustomStyleResponse;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.customOrder.CustomCategory;
import semicolon.africa.waylchub.model.customOrder.CustomStyle;
import semicolon.africa.waylchub.repository.customOrderRepository.CustomCategoryRepository;
import semicolon.africa.waylchub.repository.customOrderRepository.CustomStyleRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Composes the customer-facing custom catalog response: categories with their
 * active styles embedded.
 *
 * Uses a single batched query for styles across all categories rather than
 * N+1 lookups — important when the catalog has 10+ categories with 4+ styles each.
 *
 * Sorting (both for categories and styles) is null-safe on sortOrder.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomCatalogService {

    private static final Comparator<CustomCategory> CATEGORY_ORDER =
            Comparator.comparingInt((CustomCategory c) ->
                            c.getSortOrder() != null ? c.getSortOrder() : 100)
                    .thenComparing(c -> c.getName() != null ? c.getName() : "");

    private static final Comparator<CustomStyle> STYLE_ORDER =
            Comparator.comparingInt((CustomStyle s) ->
                            s.getSortOrder() != null ? s.getSortOrder() : 100)
                    .thenComparing(s -> s.getName() != null ? s.getName() : "");

    private final CustomCategoryRepository categoryRepository;
    private final CustomStyleRepository styleRepository;

    /**
     * Returns all active categories, each with its active styles embedded.
     * Customer-facing — used by the /custom landing page on web and mobile.
     */
    public List<CustomCategoryResponse> listActiveWithStyles() {
        List<CustomCategory> categories = categoryRepository.findByActiveTrue()
                .stream()
                .sorted(CATEGORY_ORDER)
                .toList();

        if (categories.isEmpty()) return List.of();

        // Batched query: all styles for all categories in one round trip
        Set<String> slugs = categories.stream()
                .map(CustomCategory::getSlug)
                .collect(Collectors.toSet());

        List<CustomStyle> allStyles = styleRepository
                .findByCategorySlugInAndActiveTrue(slugs);

        Map<String, List<CustomStyle>> stylesByCategory = allStyles.stream()
                .sorted(STYLE_ORDER)
                .collect(Collectors.groupingBy(CustomStyle::getCategorySlug));

        return categories.stream()
                .map(c -> buildResponse(c, stylesByCategory.getOrDefault(c.getSlug(), List.of())))
                .toList();
    }

    /**
     * Single active category by slug, with its active styles.
     * Used by the wizard when the customer opens a specific category.
     */
    public CustomCategoryResponse getActiveBySlugWithStyles(String slug) {
        CustomCategory category = categoryRepository.findBySlug(slug)
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Custom category not found or inactive: " + slug));

        List<CustomStyle> styles = styleRepository
                .findByCategorySlugAndActiveTrue(category.getSlug())
                .stream()
                .sorted(STYLE_ORDER)
                .toList();

        return buildResponse(category, styles);
    }

    /**
     * Common builder: turns a category + its styles into the response shape
     * the mobile/web clients expect, using the existing DTO's from() factory
     * + setSampleStyles() setter pattern.
     */
    private CustomCategoryResponse buildResponse(CustomCategory category, List<CustomStyle> styles) {
        CustomCategoryResponse response = CustomCategoryResponse.from(category);
        List<CustomStyleResponse> styleResponses = styles.stream()
                .map(CustomStyleResponse::from)
                .toList();
        response.setSampleStyles(styleResponses);
        return response;
    }
}