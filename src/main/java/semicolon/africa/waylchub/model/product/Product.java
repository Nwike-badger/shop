package semicolon.africa.waylchub.model.product;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "products")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Product {

    @Id
    private String id;

    private String name;

    private String description;

    private BigDecimal price;

    private List<String> imageUrls;

    private String category;

    private String subCategory;

    private int quantityAvailable;

    private int quantitySold;

    private boolean isBestSeller;

    private boolean isNewArrival;

    private LocalDateTime createdAt;

    private String brand;

    private double rating;
}
