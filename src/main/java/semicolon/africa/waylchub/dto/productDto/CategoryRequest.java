package semicolon.africa.waylchub.dto.productDto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CategoryRequest {
    @NotNull(message = "Name is required")
    private String name;

    @NotNull(message = "Slug is required")
    private String slug;

    private String parentSlug;
    private String description;

    private String imageUrl;
}
