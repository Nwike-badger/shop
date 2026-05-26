package semicolon.africa.waylchub.dto.customOrderDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import semicolon.africa.waylchub.model.customOrder.CustomCategory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for a custom category. Built via the static {@link #from} factory
 * — the caller is responsible for adding sampleStyles separately (admin and
 * public catalog do this differently: admin attaches inactive styles too, public
 * only attaches active ones).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomCategoryResponse {

    private String slug;
    private String name;
    private String tagline;
    private String description;
    private String genderHint;
    private BigDecimal priceFrom;
    private BigDecimal maxPrice;
    private String leadTime;
    private String accent;
    private String coverImageUrl;
    private String silhouettePath;
    private String measurementSet;
    private List<CustomStyleResponse> sampleStyles;  // ← external type, not inner class
    private Long orderCount;
    private Integer sortOrder;
    private Boolean active;

    public static CustomCategoryResponse from(CustomCategory c) {
        CustomCategoryResponse r = new CustomCategoryResponse();
        r.setSlug(c.getSlug());
        r.setName(c.getName());
        r.setTagline(c.getTagline());
        r.setDescription(c.getDescription());
        r.setGenderHint(c.getGenderHint());
        r.setPriceFrom(c.getPriceFrom());
        r.setMaxPrice(c.getMaxPrice());
        r.setLeadTime(c.getLeadTime());
        r.setAccent(c.getAccent());
        r.setCoverImageUrl(c.getCoverImageUrl());
        r.setSilhouettePath(c.getSilhouettePath());
        r.setMeasurementSet(c.getMeasurementSet());
        r.setSortOrder(c.getSortOrder());
        r.setActive(c.getActive());
        return r;
    }
}