package semicolon.africa.waylchub.dto.userDTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {
    private String id;
    private String username;
    private String email;
}
