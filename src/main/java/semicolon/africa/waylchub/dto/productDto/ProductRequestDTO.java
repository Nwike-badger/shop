package semicolon.africa.waylchub.dto.productDto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductRequestDTO {
    private String name;
    private String description;
    private BigDecimal price;
    private List<String> imageUrls;
    private String category;
    private String subCategory;
    private int quantityAvailable;
    private String brand;
    private boolean isBestSeller;
    private boolean isNewArrival;
}
