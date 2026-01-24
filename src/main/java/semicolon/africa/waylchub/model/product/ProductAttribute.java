package semicolon.africa.waylchub.model.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductAttribute {
    private String name;  // e.g., "Screen Size", "Voltage", "Fabric"
    private String value; // e.g., "55 inch", "220V", "Cotton"
}
