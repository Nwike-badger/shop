package semicolon.africa.waylchub.model.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductImage {
    private String url;
    private boolean isPrimary;
    private MediaType type;

    public enum MediaType {
        IMAGE,
        VIDEO
    }
}