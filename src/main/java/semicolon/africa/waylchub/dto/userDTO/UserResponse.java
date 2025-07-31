package semicolon.africa.waylchub.dto.userDTO;

import lombok.Builder;
import lombok.Data;
import semicolon.africa.waylchub.model.product.Address;
import semicolon.africa.waylchub.model.user.RoleName;

import java.util.Set;

@Data
@Builder
public class UserResponse {
    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private Address defaultAddress;
    private Set<RoleName> roles;
}
