package semicolon.africa.waylchub.controller.customOrder;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.customOrderDto.CustomCategoryResponse;
import semicolon.africa.waylchub.service.customOrderService.CustomCatalogService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/custom-catalog")
@RequiredArgsConstructor
public class CustomCatalogController {

    private final CustomCatalogService catalogService;

    /** All active categories with their active styles embedded. */
    @GetMapping("/categories")
    public List<CustomCategoryResponse> listCategories() {
        return catalogService.listActiveWithStyles();
    }

    /** Single category by slug with its styles. */
    @GetMapping("/categories/{slug}")
    public CustomCategoryResponse getCategory(@PathVariable String slug) {
        return catalogService.getActiveBySlugWithStyles(slug);
    }
}