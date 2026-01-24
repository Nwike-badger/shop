package semicolon.africa.waylchub.dto.productDto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class ProductFilterRequest {

    private String categorySlug;

    // Dynamic attributes: Color=Black, Power=3000W
    private Map<String, String> attributes;

    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String keyword;
}
