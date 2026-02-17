package semicolon.africa.waylchub.dto.productDto;

import lombok.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryTreeResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String slug;

    // FIX: initialize to empty list, never null
    private List<CategoryTreeResponse> children = new ArrayList<>();
}