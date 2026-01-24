package semicolon.africa.waylchub.model.product;

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
    private Category parent;

    // SCALABILITY FIX: "Materialized Path"
    // Stores the full breadcrumb ID string: ",Fashion_ID,Mens_ID,Shoes_ID,"
    // This allows finding all children in ONE query using Regex.
    @Indexed
    private String lineage;

    private boolean isActive = true;
}