package semicolon.africa.waylchub.dto.customOrderDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import semicolon.africa.waylchub.model.customOrder.CustomStyle;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomStyleResponse {

    private String slug;
    private String categorySlug;
    private String name;
    private String tone;
    private String imageUrl;
    private String imagePublicId;
    private String description;
    private Integer sortOrder;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public static CustomStyleResponse from(CustomStyle s) {
        if (s == null) return null;

        return CustomStyleResponse.builder()
                .slug(s.getSlug())
                .categorySlug(s.getCategorySlug())
                .name(s.getName())
                .tone(s.getTone())
                .imageUrl(s.getImageUrl())
                .imagePublicId(s.getImagePublicId())
                .description(s.getDescription())
                .sortOrder(s.getSortOrder())
                .active(s.getActive())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}