package semicolon.africa.waylchub.dto.productDto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ProductRequestDTO {
    private String name;
    private String description;
    private String category;
    private String subCategory;
    private List<String> tags;
    private String brand;

    private String sku;
    private Map<String, String> attributes;
    private BigDecimal price;
    private BigDecimal oldPrice;
    private int quantity;
    private List<String> imageUrls;
    private String discountPercentage;
    private String discountColorCode;

    private int totalReviews;
    private double averageRating;

}
