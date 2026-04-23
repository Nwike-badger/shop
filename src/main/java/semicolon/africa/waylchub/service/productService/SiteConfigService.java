package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.config.CacheConfig;
import semicolon.africa.waylchub.model.product.SiteConfig;
import semicolon.africa.waylchub.repository.productRepository.SiteConfigRepository;

@Service
@RequiredArgsConstructor
public class SiteConfigService {

    private final SiteConfigRepository siteConfigRepository;

    @Cacheable(value = CacheConfig.SITE_CONFIG_CACHE, key = "'cat_bar'")  // ← constant, not string
    public SiteConfig getCatBarConfig() {
        return siteConfigRepository.findById("cat_bar")
                .orElseGet(SiteConfig::new);
    }

    @CacheEvict(value = CacheConfig.SITE_CONFIG_CACHE, key = "'cat_bar'")  // ← evict only, no @Cacheable
    public SiteConfig updateCatBarConfig(String parentSlug) {
        SiteConfig config = siteConfigRepository.findById("cat_bar")
                .orElseGet(SiteConfig::new);
        config.setId("cat_bar");
        config.setCatBarParentSlug(
                parentSlug == null || parentSlug.isBlank() ? null : parentSlug
        );
        return siteConfigRepository.save(config);
    }
}