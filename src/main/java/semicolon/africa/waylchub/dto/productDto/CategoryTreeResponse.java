package semicolon.africa.waylchub.dto.productDto;

import lombok.Data;
import java.io.Serializable; // <--- 1. Import this
import java.util.List;

@Data
public class CategoryTreeResponse implements Serializable {

    // 3. Add this ID to prevent version conflicts
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String slug;
    private List<CategoryTreeResponse> children;
}