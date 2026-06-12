package semicolon.africa.waylchub.model.product;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "site_config")
public class SiteConfig {
    @Id
    private String id = "cat_bar";
    private String catBarParentSlug;
    private String catBarMode;          // PARENT | LEAVES | DEPTH
    private Integer catBarDepth;

    // ── NEW: server-side curation ──
    private java.util.List<String> catBarOrder;        // ordered slugs (pinned-first)
    private java.util.List<String> catBarHidden;       // hidden slugs
    private java.util.Map<String, String> catBarImageOverrides; // slug → image URL
}