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

    @CacheEvict(value = CacheConfig.SITE_CONFIG_CACHE, key = "'cat_bar'")
    public SiteConfig updateCatBarConfig(SiteConfig incoming) {
        SiteConfig config = siteConfigRepository.findById("cat_bar").orElseGet(SiteConfig::new);
        config.setId("cat_bar");
        config.setCatBarParentSlug(
                incoming.getCatBarParentSlug() == null || incoming.getCatBarParentSlug().isBlank()
                        ? null : incoming.getCatBarParentSlug());
        config.setCatBarMode(incoming.getCatBarMode() == null || incoming.getCatBarMode().isBlank()
                ? "PARENT" : incoming.getCatBarMode());
        config.setCatBarDepth(incoming.getCatBarDepth());
        config.setCatBarOrder(incoming.getCatBarOrder());
        config.setCatBarHidden(incoming.getCatBarHidden());
        config.setCatBarImageOverrides(incoming.getCatBarImageOverrides());
        return siteConfigRepository.save(config);
    }
}