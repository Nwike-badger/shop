package semicolon.africa.waylchub.service.customOrderService;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Server-side mirror of the frontend's {@code CATEGORIES} array.
 *
 * Why duplicate this here?
 *   - We need to validate categoryId on submit ("agbada" yes, "trousseaux" no).
 *   - We need the human-readable name to denormalize onto the saved order.
 *   - We need priceFrom for sanity-checking admin quotes (warn if quote ≤ priceFrom).
 *
 * If a category set ever needs to be admin-managed (add/remove categories from
 * a UI), promote this to a MongoDB collection. For now, code-as-config is fine —
 * the 11 categories are stable, and adding a new one is a one-liner here +
 * matching entry on the frontend.
 *
 * KEEP IN SYNC with /src/components/custom/customDesignData.js → CATEGORIES.
 */
@Component
public class CustomCategoryRegistry {

    @Data
    @AllArgsConstructor
    public static class Entry {
        private String id;
        private String name;
        private String genderHint;   // "men", "women", "unisex"
        private BigDecimal priceFrom;
        private String leadTime;
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("agbada",       "Agbada",          "men",    BigDecimal.valueOf(35000), "14-21 days"),
            new Entry("senator",      "Senator",         "men",    BigDecimal.valueOf(22000), "7-14 days"),
            new Entry("suit",         "Suit",            "men",    BigDecimal.valueOf(45000), "14-21 days"),
            new Entry("kaftan",       "Kaftan",          "unisex", BigDecimal.valueOf(18000), "7-10 days"),
            new Entry("shirt",        "Shirt",           "unisex", BigDecimal.valueOf(12000), "5-7 days"),
            new Entry("trouser",      "Trouser",         "unisex", BigDecimal.valueOf(10000), "5-7 days"),
            new Entry("dress",        "Dress",           "women",  BigDecimal.valueOf(18000), "7-14 days"),
            new Entry("iro-buba",     "Iro & Buba",      "women",  BigDecimal.valueOf(25000), "10-14 days"),
            new Entry("skirt-blouse", "Skirt & Blouse",  "women",  BigDecimal.valueOf(20000), "7-14 days"),
            new Entry("jumpsuit",     "Jumpsuit",        "women",  BigDecimal.valueOf(22000), "10-14 days")
    );

    private static final Map<String, Entry> BY_ID = ENTRIES.stream()
            .collect(Collectors.toUnmodifiableMap(Entry::getId, e -> e));

    public List<Entry> all() {
        return ENTRIES;
    }

    public Optional<Entry> findById(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public Entry getRequired(String id) {
        return findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Custom category not found: " + id));
    }
}