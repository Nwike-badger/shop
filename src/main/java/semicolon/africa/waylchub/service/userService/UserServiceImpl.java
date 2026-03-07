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
import semicolon.africa.waylchub.model.user.User;
import semicolon.africa.waylchub.model.user.UserType;
import semicolon.africa.waylchub.repository.userRepository.UserRepository;

import java.util.Collections;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleService roleService; // ✅ RoleService used — RoleRepository removed
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse register(UserRegistrationRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already in use");
        }

        Role role = roleService.getRoleByUserType(UserType.CUSTOMER); // ✅ dynamic, not hardcoded

        User user = User.builder()
                .username(request.getEmail())
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .password(passwordEncoder.encode(request.getPassword()))
                // ✅ CRITICAL: Use HashSet, not Collections.singleton()
                // Collections.singleton() returns an IMMUTABLE set.
                // If promoteToAdmin() tries to .add() to it later, it throws
                // UnsupportedOperationException. HashSet is always safe.
                .roles(new HashSet<>(Collections.singleton(role)))
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
    public UserResponse getCurrentUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .defaultAddress(user.getAddress())
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

    /**
     * Promotes an existing user to ADMIN role.
     *
     * This is safe because:
     * - The user is loaded FRESH from the database (Spring Data MongoDB deserializes
     *   roles into a mutable HashSet regardless of how they were originally created)
     * - getRoles().add() on a DB-loaded entity is always safe
     * - The new HashSet in register() above also prevents any edge case with
     *   newly-created in-memory users
     *
     * Access: ROLE_ADMIN only (enforced in UserController)
     */
    @Override
    @Transactional
    public UserResponse promoteToAdmin(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        Role adminRole = roleService.getRoleByUserType(UserType.ADMIN);

        // Defensive: ensure the set is mutable before adding
        if (!(user.getRoles() instanceof HashSet)) {
            user.setRoles(new HashSet<>(user.getRoles()));
        }

        // Idempotent: if already admin, adding the same role is a no-op
        user.getRoles().add(adminRole);
        User updatedUser = userRepository.save(user);

        return UserResponse.builder()
                .id(updatedUser.getId())
                .email(updatedUser.getEmail())
                .firstName(updatedUser.getFirstName())
                .lastName(updatedUser.getLastName())
                .build();
    }
}