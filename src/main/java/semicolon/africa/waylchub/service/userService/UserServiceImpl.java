package semicolon.africa.waylchub.service.userService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.userDTO.UpdateUserAddressRequest;
import semicolon.africa.waylchub.dto.userDTO.UserRegistrationRequest;
import semicolon.africa.waylchub.dto.userDTO.UserResponse;
import semicolon.africa.waylchub.exception.UserAlreadyExistsException;
import semicolon.africa.waylchub.exception.UserNotFoundException;
import semicolon.africa.waylchub.model.product.Address;
import semicolon.africa.waylchub.model.user.Role;
import semicolon.africa.waylchub.model.user.RoleName;
import semicolon.africa.waylchub.model.user.User;
import semicolon.africa.waylchub.repository.userRepository.RoleRepository;
import semicolon.africa.waylchub.repository.userRepository.UserRepository;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse register(UserRegistrationRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already in use");
        }

        Role role = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Default role not found"));

        User user = User.builder()
                .username(request.getEmail())
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(Collections.singleton(role))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .verified(false)
                .build();

        userRepository.save(user);

        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .build();
    }

    @Override
    @Transactional
    public UserResponse updateUserAddress(String userId, UpdateUserAddressRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));


        Address newAddress = request.getAddress();
        user.setAddress(newAddress);

        User updatedUser = userRepository.save(user);


        return UserResponse.builder()
                .id(updatedUser.getId())
                .email(updatedUser.getEmail())
                .firstName(updatedUser.getFirstName())
                .lastName(updatedUser.getLastName())
                .defaultAddress(updatedUser.getAddress())
                .build();
    }
}
