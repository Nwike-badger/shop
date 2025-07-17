package semicolon.africa.waylchub.dto.productDto;


import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SearchProductRequest {
    private List<String> tags;
    private String brand;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
}
