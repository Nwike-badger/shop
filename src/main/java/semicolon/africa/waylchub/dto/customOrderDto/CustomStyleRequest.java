package semicolon.africa.waylchub.dto.customOrderDto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomStyleRequest {

    /** Slug required on create only. */
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must be lowercase letters, numbers, or hyphens only")
    @Size(min = 2, max = 80)
    private String slug;

    /** Required on create, optional on update — validated in service layer. */
    @Size(min = 1, max = 80)
    private String name;

    @Size(max = 100)
    private String tone;

    @Size(max = 1000)
    private String imageUrl;

    @Size(max = 200)
    private String imagePublicId;

    @Size(max = 1000)
    private String description;

    private Integer sortOrder;

    private Boolean active;
}