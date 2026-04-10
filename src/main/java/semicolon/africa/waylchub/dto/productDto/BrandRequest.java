package semicolon.africa.waylchub.dto.productDto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BrandRequest {

    @NotBlank(message = "Brand name is required")
    private String name;

    @NotBlank(message = "Brand slug is required")
    private String slug;

    private String description;
    private String logoUrl;
    private String website;
}