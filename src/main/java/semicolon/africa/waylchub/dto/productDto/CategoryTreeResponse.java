package semicolon.africa.waylchub.dto.productDto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Flat, Redis-safe DTO for the category tree and featured-category endpoints.
 *
 * Rules that keep this safe in Redis:
 *  - No @DBRef fields (no MongoDB proxies)
 *  - No back-references (no parent pointer)
 *  - No Jackson @JsonTypeInfo needed — it's a concrete class, not polymorphic
 *  - children defaults to an empty list, never null (avoids NPE in frontend)
 */
@Data
@NoArgsConstructor
public class CategoryTreeResponse {

    private String id;
    private String name;
    private String slug;
    private String description;
    private String imageUrl;
    private boolean isFeatured;
    private Integer displayOrder;
    private boolean isActive;

    /**
     * Populated for tree responses; left empty for featured-category responses
     * where a flat list is sufficient.
     */
    private List<CategoryTreeResponse> children = new ArrayList<>();
}