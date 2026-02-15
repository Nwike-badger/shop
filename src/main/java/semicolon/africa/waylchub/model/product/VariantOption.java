package semicolon.africa.waylchub.model.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VariantOption {
    private String name; // e.g. "Color"
    private List<String> values; // e.g. ["Red", "Blue", "Green"]
}