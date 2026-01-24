package semicolon.africa.waylchub.model.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductImage {
    private String url;
    private boolean isPrimary; // True for the main image shown in search results
}
