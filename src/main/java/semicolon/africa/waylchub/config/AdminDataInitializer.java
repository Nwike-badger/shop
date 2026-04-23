//package semicolon.africa.waylchub.config;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import semicolon.africa.waylchub.model.user.Role;
//import semicolon.africa.waylchub.model.user.RoleName;
//import semicolon.africa.waylchub.model.user.User;
//import semicolon.africa.waylchub.model.user.UserType;
//import semicolon.africa.waylchub.repository.userRepository.RoleRepository;
//import semicolon.africa.waylchub.repository.userRepository.UserRepository;
//import semicolon.africa.waylchub.service.userService.RoleService;
//
//import java.util.HashSet;
//import java.util.Set;
//
///**
// * Runs ONCE on every application startup.
// *
// * Responsibilities:
// *   1. Seed ROLE_USER and ROLE_ADMIN into the roles collection if they don't exist
// *   2. Create the first superadmin account if it doesn't exist
// *
// * The email/password are read from application.properties so they are never
// * hardcoded in source code. Set these in your environment or properties file:
// *
// *   app.admin.email=admin@waylchub.com
// *   app.admin.password=ChangeMe123!
// *
// * On subsequent startups, if the admin already exists, nothing is touched.
// */
//@Slf4j
//@Configuration
//@RequiredArgsConstructor
//public class AdminDataInitializer {
//
//    private final RoleRepository roleRepository;
//    private final UserRepository userRepository;
//    private final PasswordEncoder passwordEncoder;
//
//    @Value("${app.admin.email}")
//    private String adminEmail;
//
//    @Value("${app.admin.password}")
//    private String adminPassword;
//
//    @Bean
//    public CommandLineRunner seedData() {
//        return args -> {
//            // ── Step 1: Seed roles ────────────────────────────────────────────
//            Role userRole = seedRole(RoleName.ROLE_USER);
//            Role adminRole = seedRole(RoleName.ROLE_ADMIN);
//
//            // ── Step 2: Seed first admin account ─────────────────────────────
//            if (userRepository.existsByEmail(adminEmail)) {
//                log.info("Admin account [{}] already exists — skipping seed.", adminEmail);
//                return;
//            }
//
//            User admin = User.builder()
//                    .username(adminEmail)
//                    .email(adminEmail)
//                    .firstName("Super")
//                    .lastName("Admin")
//                    .password(passwordEncoder.encode(adminPassword))
//                    .roles(new HashSet<>(Set.of(userRole, adminRole))) // both roles
//                    .enabled(true)
//                    .accountNonExpired(true)
//                    .accountNonLocked(true)
//                    .credentialsNonExpired(true)
//                    .verified(true)
//                    .build();
//
//            userRepository.save(admin);
//            log.info("✅ First admin account created: [{}]", adminEmail);
//            log.warn("⚠️  CHANGE THE DEFAULT ADMIN PASSWORD IMMEDIATELY after first login!");
//        };
//    }
//
//    private Role seedRole(RoleName name) {
//        return roleRepository.findByName(name).orElseGet(() -> {
//            Role role = Role.builder().name(name).build();
//            roleRepository.save(role);
//            log.info("Seeded role: {}", name);
//            return role;
//        });
//    }
//}