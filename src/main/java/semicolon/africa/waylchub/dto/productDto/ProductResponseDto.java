package semicolon.africa.waylchub.dto.productDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductResponseDto {
    private String id;
    private String name;
    private BigDecimal price;
    private String description;
    private List<String> imageUrls;
    private int quantityAvailable;
    private String category;
    private String subCategory;
    private int quantitySold;
    private String brand;
}
