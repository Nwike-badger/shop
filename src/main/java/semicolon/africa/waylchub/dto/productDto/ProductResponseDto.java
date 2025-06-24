package semicolon.africa.waylchub.dto.productDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal; // Not directly used here
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map; // Not directly used here

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponseDto {
    private String id;
    private String name;
    private String description;
    private String category;
    private String subCategory;
    private List<String> tags;
    private String brand;
    private List<ProductVariantResponseDto> variants;
    private LocalDateTime createdAt;
}