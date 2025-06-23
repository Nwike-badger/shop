package semicolon.africa.waylchub.service.userService;

import semicolon.africa.waylchub.model.user.Role;
import semicolon.africa.waylchub.model.user.UserType;

public interface RoleService {
    Role getRoleByUserType(UserType userType);
}
