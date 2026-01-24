package semicolon.africa.waylchub.model.product;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "brands")
public class Brand {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name; // Nexus, Nike, Hisense

    @Indexed(unique = true)
    private String slug;

    private String logoUrl;

    private String description;
}
