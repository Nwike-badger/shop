package semicolon.africa.waylchub.dto.productDto;

import lombok.Data;

import java.util.List;
@Data
public class CategoryTreeResponse {


    private String name;
    private String slug;
    private List<CategoryTreeResponse> children;

    // getters & setters
}
