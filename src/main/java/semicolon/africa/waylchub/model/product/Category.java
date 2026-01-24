package semicolon.africa.waylchub.model.product;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "categories")
public class Category {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    @Indexed(unique = true)
    private String slug; // url-friendly: "small-appliances"

    private String description;

    private String imageUrl; // Category thumbnail

    // The Parent Category (e.g., "Small Appliances" has parent "Appliances")
    // If null, this is a Root Category.
    @DBRef(lazy = true)
    private Category parent;

    // OPTIONAL: Materialized path for fast "breadcrumbing"
    // e.g., "appliances/small-appliances/blenders"
    private String categoryPath;

    private boolean isActive = true;
}
