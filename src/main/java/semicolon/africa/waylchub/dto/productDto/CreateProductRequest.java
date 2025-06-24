package semicolon.africa.waylchub.dto.productDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {
    @NotBlank(message = "Product name cannot be empty")
    private String name;
    private String description;
    @NotBlank(message = "Category cannot be empty")
    private String category;
    private String subCategory;
    private List<String> tags;
    private String brand;
    @NotNull(message = "Product must have at least one variant")
    @Size(min = 1, message = "Product must have at least one variant")
    private List<ProductVariantRequest> variants;
}