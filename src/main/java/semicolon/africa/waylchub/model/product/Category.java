package semicolon.africa.waylchub.model.product;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A node in the category tree, e.g.
 *
 *   Men                (slug: men)
 *    ├─ Shirts         (slug: men-shirts)
 *    └─ Trousers       (slug: men-trousers)
 *   Women              (slug: women)
 *    ├─ Shirts         (slug: women-shirts)
 *    └─ Trousers       (slug: women-trousers)
 *
 * CHANGE: `name` is no longer globally unique. Two categories can both be
 * called "Shirts" as long as they have different parents (checked in
 * CategoryService). The SLUG is still unique — it's what URLs and products use.
 *
 * Do NOT put @Indexed back on `name`: the old unique index is dropped at
 * startup by CategoryMigration, and re-adding any index named "name" with
 * different options would clash with it.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "parent")
@EqualsAndHashCode(exclude = "parent")
@Document(collection = "categories")
public class Category {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String slug;

    private String description;
    private String imageUrl;

    /**
     * @JsonIgnore — stops Jackson walking parent → parent.parent → … when
     * serialising to Redis. The tree is rebuilt in memory in CategoryService.
     */
    @DBRef
    @JsonIgnore
    private Category parent;

    /** Ancestor ids, comma-wrapped: ",rootId,childId," ("," for a root). */
    @Indexed
    private String lineage;

    @Builder.Default
    private boolean isFeatured = false;
    private Integer displayOrder;
    @Builder.Default
    private boolean isActive = true;
}