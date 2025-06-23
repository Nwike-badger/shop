package semicolon.africa.waylchub.service.userService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.exception.RoleNotFoundException;
import semicolon.africa.waylchub.model.user.Role;
import semicolon.africa.waylchub.model.user.RoleName;
import semicolon.africa.waylchub.model.user.UserType;
import semicolon.africa.waylchub.repository.userRepository.RoleRepository;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    @Override
    public Role getRoleByUserType(UserType userType) {
        RoleName roleName = switch (userType) {
            case CUSTOMER -> RoleName.ROLE_USER;
            case ADMIN -> RoleName.ROLE_ADMIN;
        };

        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new RoleNotFoundException("Role not found"));
    }
}
