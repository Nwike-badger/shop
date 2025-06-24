package semicolon.africa.waylchub.model.product;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map; // Not directly used in Product, but good to have if ProductVariant is here

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
    private String category;
    private String subCategory;
    private List<String> tags;
    private String brand;
    private LocalDateTime createdAt;

    @Field("variants")
    private List<ProductVariant> variants;
}