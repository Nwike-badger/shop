package semicolon.africa.waylchub.model.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import semicolon.africa.waylchub.model.product.Address;
import semicolon.africa.waylchub.model.product.Cart;
import semicolon.africa.waylchub.model.user.Role;

import java.time.LocalDateTime;
import java.util.Set;

@Document(collection = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User {

    @Id
    private String id;

    private String username;

    private String email;

    private String password;

    private Set<Role> roles;

    private boolean enabled;

    private boolean accountNonExpired;

    private boolean accountNonLocked;

    private boolean credentialsNonExpired;

    private boolean verified;

    private String phoneNumber;

    private String firstName;

    private String lastName;

    private Address address;

    private Cart cart;

    @CreatedDate
    private LocalDateTime createdAt;


}
