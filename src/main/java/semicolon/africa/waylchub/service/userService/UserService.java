package semicolon.africa.waylchub.service.userService;

import semicolon.africa.waylchub.dto.userDTO.UpdateUserAddressRequest;
import semicolon.africa.waylchub.dto.userDTO.UserRegistrationRequest;
import semicolon.africa.waylchub.dto.userDTO.UserResponse;

public interface UserService {
    UserResponse register(UserRegistrationRequest request);
    UserResponse updateUserAddress(String userId, UpdateUserAddressRequest request);

    UserResponse getCurrentUser(String userId);
}
