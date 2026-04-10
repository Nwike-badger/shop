package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.productDto.BrandRequest;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.product.Brand;
import semicolon.africa.waylchub.repository.productRepository.BrandRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    /* ─── READ ─── */

    @Cacheable(value = "brands")
    public List<Brand> getAllBrands() {
        return brandRepository.findAll();
    }

    public Brand getBrand(String slug) {
        return brandRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found: " + slug));
    }

    /* ─── CREATE ─── */

    @Transactional
    @CacheEvict(value = "brands", allEntries = true)
    public Brand createBrand(BrandRequest req) {
        if (brandRepository.findBySlug(req.getSlug()).isPresent()) {
            throw new IllegalArgumentException(
                    "A brand with slug '" + req.getSlug() + "' already exists.");
        }
        if (brandRepository.findByNameIgnoreCase(req.getName()).isPresent()) {
            throw new IllegalArgumentException(
                    "A brand named '" + req.getName() + "' already exists.");
        }

        Brand brand = new Brand();
        brand.setName(req.getName());
        brand.setSlug(req.getSlug());
        brand.setDescription(req.getDescription());
        brand.setLogoUrl(req.getLogoUrl());
        brand.setWebsite(req.getWebsite());

        return brandRepository.save(brand);
    }

    /* ─── UPDATE ─── */

    @Transactional
    @CacheEvict(value = "brands", allEntries = true)
    public Brand updateBrand(String slug, BrandRequest req) {
        Brand brand = brandRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found: " + slug));

        // Check new name doesn't clash with a DIFFERENT brand
        brandRepository.findByNameIgnoreCase(req.getName())
                .filter(existing -> !existing.getId().equals(brand.getId()))
                .ifPresent(b -> { throw new IllegalArgumentException(
                        "Another brand is already named '" + req.getName() + "'."); });

        brand.setName(req.getName());
        brand.setDescription(req.getDescription());
        brand.setLogoUrl(req.getLogoUrl());
        brand.setWebsite(req.getWebsite());
        // Slug is intentionally immutable after creation (products reference it)

        return brandRepository.save(brand);
    }

    /* ─── DELETE ─── */

    @Transactional
    @CacheEvict(value = "brands", allEntries = true)
    public void deleteBrand(String slug) {
        Brand brand = brandRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found: " + slug));

        // Guard: don't delete a brand that still has products assigned
        boolean hasProducts = productRepository.existsByBrandSlug(slug);
        if (hasProducts) {
            long count = productRepository.countByBrandSlug(slug);
            throw new IllegalStateException(
                    "Cannot delete brand '" + brand.getName() + "' — it has " + count +
                            " product" + (count > 1 ? "s" : "") + " assigned. " +
                            "Reassign or remove those products first.");
        }

        brandRepository.delete(brand);
    }

    /* ─── STATS (used by BrandManager UI) ─── */

    public long countProductsForBrand(String slug) {
        return productRepository.countByBrandSlug(slug);
    }
}