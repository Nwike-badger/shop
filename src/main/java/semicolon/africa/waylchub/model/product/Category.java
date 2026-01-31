package semicolon.africa.waylchub.model.product;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

@Data
@Document(collection = "categories")
public class Category {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    @Indexed(unique = true)
    private String slug;

    private String description;
    private String imageUrl;

    @DBRef(lazy = true)
    @JsonIgnore
    private Category parent;


    @Indexed
    private String lineage;


    private boolean isActive = true;

    @Indexed
    private boolean isFeatured = false;

    private Integer displayOrder;
}