package semicolon.africa.waylchub.model.product;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "site_config")
public class SiteConfig {
    @Id
    private String id = "cat_bar";
    private String catBarParentSlug; // null = show root categories
}
