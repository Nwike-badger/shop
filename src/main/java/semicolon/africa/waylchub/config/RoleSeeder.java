package semicolon.africa.waylchub.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import semicolon.africa.waylchub.model.user.Role;
import semicolon.africa.waylchub.model.user.RoleName;
import semicolon.africa.waylchub.repository.userRepository.RoleRepository;

@Component
@RequiredArgsConstructor
public class RoleSeeder {

    private final RoleRepository roleRepository;

    @PostConstruct
    public void seedRoles() {
        for (RoleName roleName : RoleName.values()) {
            roleRepository.findByName(roleName)
                    .orElseGet(() -> roleRepository.save(Role.builder().name(roleName).build()));
        }
    }
}
