package semicolon.africa.waylchub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.model.product.SiteConfig;
import semicolon.africa.waylchub.service.productService.SiteConfigService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class SiteConfigController {

    private final SiteConfigService siteConfigService;

    @GetMapping("/cat-bar")
    public ResponseEntity<SiteConfig> getCatBarConfig() {
        return ResponseEntity.ok(siteConfigService.getCatBarConfig());
    }

    @PutMapping("/cat-bar")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<SiteConfig> updateCatBarConfig(
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                siteConfigService.updateCatBarConfig(body.get("catBarParentSlug"))
        );
    }
}