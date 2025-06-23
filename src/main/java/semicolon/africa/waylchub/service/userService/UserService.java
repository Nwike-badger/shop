package semicolon.africa.waylchub.service.userService;

import semicolon.africa.waylchub.dto.userDTO.UserRegistrationRequest;
import semicolon.africa.waylchub.dto.userDTO.UserResponse;

public interface UserService {
    UserResponse register(UserRegistrationRequest request);
}
