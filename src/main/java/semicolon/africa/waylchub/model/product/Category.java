package semicolon.africa.waylchub.model.product;

import lombok.*; // Import all
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter // Use Getter/Setter instead of @Data for safer control
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    // Self-referencing parent
    @DBRef(lazy = true)
    @ToString.Exclude          // <--- ADD THIS TO PREVENT CRASHES
    @EqualsAndHashCode.Exclude // <--- ADD THIS TO PREVENT CRASHES
    private Category parent;

    @Indexed
    private String lineage;

    private boolean isFeatured = false;
    private Integer displayOrder;
    private boolean isActive = true;
}