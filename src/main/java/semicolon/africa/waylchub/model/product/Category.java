package semicolon.africa.waylchub.model.product;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "parent")          // ← replaces @ToString.Exclude on the field
@EqualsAndHashCode(exclude = "parent") // ← replaces @EqualsAndHashCode.Exclude on the field
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

    /**
     * @JsonIgnore — prevents Jackson from traversing the self-referential
     * parent link when serializing to Redis.  Without this, Jackson follows
     * parent → parent.parent → … until it either hits a circular-reference
     * error or tries to serialize a MongoDB proxy and throws a 400.
     *
     * The tree is rebuilt in-memory in CategoryService.getCategoryTree()
     * using a childrenMap, so this field is only needed during that
     * in-process traversal — it never needs to be in the Redis JSON.
     */
    @DBRef
    @JsonIgnore
    private Category parent;

    @Indexed
    private String lineage;

    private boolean isFeatured = false;
    private Integer displayOrder;
    private boolean isActive = true;
}