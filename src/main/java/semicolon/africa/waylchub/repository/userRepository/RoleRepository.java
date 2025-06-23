package semicolon.africa.waylchub.repository.userRepository;

import org.springframework.data.mongodb.repository.MongoRepository;
import semicolon.africa.waylchub.model.user.Role;
import semicolon.africa.waylchub.model.user.RoleName;

import java.util.Optional;

public interface RoleRepository extends MongoRepository<Role, String> {
    Optional<Role> findByName(RoleName name);
}
