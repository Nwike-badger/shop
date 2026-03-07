package semicolon.africa.waylchub.service.userService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import semicolon.africa.waylchub.dto.userDTO.UpdateUserAddressRequest;
import semicolon.africa.waylchub.dto.userDTO.UserRegistrationRequest;
import semicolon.africa.waylchub.dto.userDTO.UserResponse;
import semicolon.africa.waylchub.exception.UserAlreadyExistsException;
import semicolon.africa.waylchub.exception.UserNotFoundException;
import semicolon.africa.waylchub.model.product.Address;
import semicolon.africa.waylchub.model.user.Role;
import semicolon.africa.waylchub.model.user.RoleName;
import semicolon.africa.waylchub.model.user.User;
import semicolon.africa.waylchub.model.user.UserType;
import semicolon.africa.waylchub.repository.userRepository.UserRepository;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl")
class UserServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock RoleService roleService;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UserServiceImpl userService;

    // ── shared fixtures ────────────────────────────────────────────────────────
    Role userRole;
    Role adminRole;

    @BeforeEach
    void setUp() {
        userRole  = Role.builder().id("role-user-id").name(RoleName.ROLE_USER).build();
        adminRole = Role.builder().id("role-admin-id").name(RoleName.ROLE_ADMIN).build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REGISTER
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("happy path: registers a new user and returns correct response")
        void register_success_returnsUserResponse() {
            UserRegistrationRequest request = buildRegistrationRequest(
                    "john@example.com", "John", "Doe", "secret123");

            when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
            when(roleService.getRoleByUserType(UserType.CUSTOMER)).thenReturn(userRole);
            when(passwordEncoder.encode("secret123")).thenReturn("hashed_secret");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId("user-001"); // simulate DB id assignment
                return u;
            });

            UserResponse response = userService.register(request);

            assertThat(response.getEmail()).isEqualTo("john@example.com");
            assertThat(response.getFirstName()).isEqualTo("John");
            assertThat(response.getLastName()).isEqualTo("Doe");
        }

        @Test
        @DisplayName("happy path: persists correct field values to the database")
        void register_success_savesCorrectUserFields() {
            UserRegistrationRequest request = buildRegistrationRequest(
                    "jane@example.com", "Jane", "Smith", "pass456");

            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(roleService.getRoleByUserType(UserType.CUSTOMER)).thenReturn(userRole);
            when(passwordEncoder.encode("pass456")).thenReturn("hashed_pass456");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.register(request);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();

            assertThat(saved.getEmail()).isEqualTo("jane@example.com");
            assertThat(saved.getUsername()).isEqualTo("jane@example.com"); // username mirrors email
            assertThat(saved.getPassword()).isEqualTo("hashed_pass456");   // raw password never stored
            assertThat(saved.isEnabled()).isTrue();
            assertThat(saved.isVerified()).isFalse();                       // email not yet verified
            assertThat(saved.isAccountNonExpired()).isTrue();
            assertThat(saved.isAccountNonLocked()).isTrue();
            assertThat(saved.isCredentialsNonExpired()).isTrue();
        }

        @Test
        @DisplayName("happy path: assigned role is CUSTOMER, not ADMIN")
        void register_success_assignsCustomerRoleOnly() {
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(roleService.getRoleByUserType(UserType.CUSTOMER)).thenReturn(userRole);
            when(passwordEncoder.encode(any())).thenReturn("hash");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.register(buildRegistrationRequest("a@b.com", "A", "B", "pw"));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            Set<Role> roles = captor.getValue().getRoles();
            assertThat(roles).hasSize(1);
            assertThat(roles.iterator().next().getName()).isEqualTo(RoleName.ROLE_USER);
            // Confirm ADMIN role is NOT present
            assertThat(roles).noneMatch(r -> r.getName() == RoleName.ROLE_ADMIN);
        }

        @Test
        @DisplayName("critical: role set is mutable (HashSet) so promoteToAdmin() won't throw")
        void register_success_roleSetIsMutable() {
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(roleService.getRoleByUserType(UserType.CUSTOMER)).thenReturn(userRole);
            when(passwordEncoder.encode(any())).thenReturn("hash");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.register(buildRegistrationRequest("a@b.com", "A", "B", "pw"));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            Set<Role> roles = captor.getValue().getRoles();

            // This is the exact operation promoteToAdmin() calls later.
            // Collections.singleton() would throw UnsupportedOperationException here.
            assertThatCode(() -> roles.add(adminRole))
                    .as("Role set must be mutable — Collections.singleton() would crash here")
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("duplicate email: throws UserAlreadyExistsException, never saves")
        void register_duplicateEmail_throwsAndNeverSaves() {
            when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

            UserRegistrationRequest request = buildRegistrationRequest(
                    "taken@example.com", "X", "Y", "pw");

            assertThatThrownBy(() -> userService.register(request))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining("Email already in use");

            // Confirm the user was never written to the DB
            verify(userRepository, never()).save(any());
            verify(passwordEncoder, never()).encode(any());
        }

        @Test
        @DisplayName("password: raw password is never stored — only the encoded hash")
        void register_passwordIsAlwaysEncoded() {
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(roleService.getRoleByUserType(any())).thenReturn(userRole);
            when(passwordEncoder.encode("plaintext")).thenReturn("$2a$hashed");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.register(buildRegistrationRequest("a@b.com", "A", "B", "plaintext"));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            assertThat(captor.getValue().getPassword())
                    .isEqualTo("$2a$hashed")
                    .doesNotContain("plaintext");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PROMOTE TO ADMIN
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("promoteToAdmin()")
    class PromoteToAdmin {

        @Test
        @DisplayName("happy path: adds ADMIN role to an existing CUSTOMER user")
        void promote_success_addsAdminRole() {
            User customer = buildUser("user-1", "customer@example.com",
                    new HashSet<>(Set.of(userRole)));

            when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
            when(roleService.getRoleByUserType(UserType.ADMIN)).thenReturn(adminRole);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UserResponse response = userService.promoteToAdmin("customer@example.com");

            // Verify the response
            assertThat(response.getEmail()).isEqualTo("customer@example.com");

            // Verify the user saved to DB has BOTH roles
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            Set<Role> savedRoles = captor.getValue().getRoles();

            assertThat(savedRoles).hasSize(2);
            assertThat(savedRoles).anyMatch(r -> r.getName() == RoleName.ROLE_USER);
            assertThat(savedRoles).anyMatch(r -> r.getName() == RoleName.ROLE_ADMIN);
        }

        @Test
        @DisplayName("idempotent: promoting an already-admin user does not duplicate the role")
        void promote_alreadyAdmin_isIdempotent() {
            // User already has both roles
            User alreadyAdmin = buildUser("user-2", "admin@example.com",
                    new HashSet<>(Set.of(userRole, adminRole)));

            when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(alreadyAdmin));
            when(roleService.getRoleByUserType(UserType.ADMIN)).thenReturn(adminRole);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Should not throw
            assertThatCode(() -> userService.promoteToAdmin("admin@example.com"))
                    .doesNotThrowAnyException();

            // Role count must still be 2, not 3
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRoles()).hasSize(2);
        }

        @Test
        @DisplayName("immutable set defence: works even if DB returns an immutable role set")
        void promote_immutableSetFromDb_doesNotThrow() {
            // Simulate a user whose roles set came back as an immutable collection
            // (e.g., from Collections.unmodifiableSet or a custom deserializer)
            User userWithImmutableRoles = buildUser("user-3", "edge@example.com",
                    Collections.unmodifiableSet(new HashSet<>(Set.of(userRole))));

            when(userRepository.findByEmail("edge@example.com"))
                    .thenReturn(Optional.of(userWithImmutableRoles));
            when(roleService.getRoleByUserType(UserType.ADMIN)).thenReturn(adminRole);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // The defensive copy in promoteToAdmin() must prevent UnsupportedOperationException
            assertThatCode(() -> userService.promoteToAdmin("edge@example.com"))
                    .doesNotThrowAnyException();

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRoles())
                    .anyMatch(r -> r.getName() == RoleName.ROLE_ADMIN);
        }

        @Test
        @DisplayName("user not found: throws UserNotFoundException, never saves")
        void promote_userNotFound_throws() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.promoteToAdmin("ghost@example.com"))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("ghost@example.com");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("correct role fetched: RoleService is called with UserType.ADMIN, not CUSTOMER")
        void promote_callsRoleServiceWithAdminType() {
            User customer = buildUser("user-1", "customer@example.com",
                    new HashSet<>(Set.of(userRole)));

            when(userRepository.findByEmail(any())).thenReturn(Optional.of(customer));
            when(roleService.getRoleByUserType(UserType.ADMIN)).thenReturn(adminRole);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.promoteToAdmin("customer@example.com");

            verify(roleService).getRoleByUserType(UserType.ADMIN);
            verify(roleService, never()).getRoleByUserType(UserType.CUSTOMER);
        }

        @Test
        @DisplayName("existing roles preserved: CUSTOMER role is not stripped during promotion")
        void promote_preservesExistingRoles() {
            User customer = buildUser("user-1", "customer@example.com",
                    new HashSet<>(Set.of(userRole)));

            when(userRepository.findByEmail(any())).thenReturn(Optional.of(customer));
            when(roleService.getRoleByUserType(UserType.ADMIN)).thenReturn(adminRole);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.promoteToAdmin("customer@example.com");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            // CUSTOMER role must still be there
            assertThat(captor.getValue().getRoles())
                    .anyMatch(r -> r.getName() == RoleName.ROLE_USER);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FULL FLOW: REGISTER → PROMOTE
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full flow: register() then promoteToAdmin()")
    class FullFlow {

        @Test
        @DisplayName("user registered as CUSTOMER can be promoted to ADMIN without any exception")
        void registerThenPromote_succeeds() {
            // ── Step 1: Register ──────────────────────────────────────────────
            when(userRepository.existsByEmail("ceo@waylchub.com")).thenReturn(false);
            when(roleService.getRoleByUserType(UserType.CUSTOMER)).thenReturn(userRole);
            when(passwordEncoder.encode(any())).thenReturn("hashed");

            // Capture what was saved during register
            ArgumentCaptor<User> registerCaptor = ArgumentCaptor.forClass(User.class);
            when(userRepository.save(registerCaptor.capture())).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId("user-ceo");
                return u;
            });

            userService.register(buildRegistrationRequest(
                    "ceo@waylchub.com", "Chidi", "Eze", "strongpass"));

            User registeredUser = registerCaptor.getValue();

            // Confirm registered user has only CUSTOMER role
            assertThat(registeredUser.getRoles()).hasSize(1);
            assertThat(registeredUser.getRoles()).allMatch(r -> r.getName() == RoleName.ROLE_USER);

            // ── Step 2: Promote ───────────────────────────────────────────────
            reset(userRepository); // fresh mock state for the second operation

            when(userRepository.findByEmail("ceo@waylchub.com"))
                    .thenReturn(Optional.of(registeredUser));
            when(roleService.getRoleByUserType(UserType.ADMIN)).thenReturn(adminRole);

            ArgumentCaptor<User> promoteCaptor = ArgumentCaptor.forClass(User.class);
            when(userRepository.save(promoteCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

            UserResponse promoted = userService.promoteToAdmin("ceo@waylchub.com");

            User promotedUser = promoteCaptor.getValue();

            // Final assertions
            assertThat(promoted.getEmail()).isEqualTo("ceo@waylchub.com");
            assertThat(promotedUser.getRoles()).hasSize(2);
            assertThat(promotedUser.getRoles()).anyMatch(r -> r.getName() == RoleName.ROLE_USER);
            assertThat(promotedUser.getRoles()).anyMatch(r -> r.getName() == RoleName.ROLE_ADMIN);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET CURRENT USER
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getCurrentUser()")
    class GetCurrentUser {

        @Test
        @DisplayName("happy path: returns correct profile fields")
        void getCurrentUser_success() {
            User user = buildUser("user-1", "john@example.com",
                    new HashSet<>(Set.of(userRole)));
            user.setFirstName("John");
            user.setLastName("Doe");

            when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

            UserResponse response = userService.getCurrentUser("user-1");

            assertThat(response.getId()).isEqualTo("user-1");
            assertThat(response.getEmail()).isEqualTo("john@example.com");
            assertThat(response.getFirstName()).isEqualTo("John");
            assertThat(response.getLastName()).isEqualTo("Doe");
        }

        @Test
        @DisplayName("user not found: throws UserNotFoundException")
        void getCurrentUser_notFound_throws() {
            when(userRepository.findById("bad-id")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getCurrentUser("bad-id"))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UPDATE ADDRESS
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateUserAddress()")
    class UpdateAddress {

        @Test
        @DisplayName("happy path: saves new address and returns updated response")
        void updateAddress_success() {
            User user = buildUser("user-1", "john@example.com",
                    new HashSet<>(Set.of(userRole)));

            Address newAddress = new Address();
            newAddress.setStreetAddress("12 Lagos St");
            newAddress.setTownCity("Lagos");

            UpdateUserAddressRequest request = new UpdateUserAddressRequest();
            request.setAddress(newAddress);

            when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UserResponse response = userService.updateUserAddress("user-1", request);

            assertThat(response.getDefaultAddress()).isNotNull();
            assertThat(response.getDefaultAddress().getTownCity()).isEqualTo("Lagos");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getAddress().getStreetAddress()).isEqualTo("12 Lagos St");
        }

        @Test
        @DisplayName("user not found: throws UserNotFoundException, never saves")
        void updateAddress_notFound_throws() {
            when(userRepository.findById("bad-id")).thenReturn(Optional.empty());

            UpdateUserAddressRequest request = new UpdateUserAddressRequest();
            request.setAddress(new Address());

            assertThatThrownBy(() -> userService.updateUserAddress("bad-id", request))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("bad-id");

            verify(userRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BUILDERS
    // ══════════════════════════════════════════════════════════════════════════

    private UserRegistrationRequest buildRegistrationRequest(
            String email, String firstName, String lastName, String password) {
        UserRegistrationRequest req = new UserRegistrationRequest();
        req.setEmail(email);
        req.setFirstName(firstName);
        req.setLastName(lastName);
        req.setPassword(password);
        return req;
    }

    private User buildUser(String id, String email, Set<Role> roles) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setUsername(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRoles(roles);
        user.setEnabled(true);
        return user;
    }
}