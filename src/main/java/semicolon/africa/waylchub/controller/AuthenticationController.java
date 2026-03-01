package semicolon.africa.waylchub.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.userDTO.*;
import semicolon.africa.waylchub.service.userService.AuthenticationService;
import semicolon.africa.waylchub.service.userService.UserService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor // ✅ Generates constructor for BOTH services
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final UserService userService; // ✅ Inject User Service here

    // ----------------------------------------------------------------
    // 1. REGISTRATION (Handled by UserService)
    // ----------------------------------------------------------------
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRegistrationRequest request) {
        // We delegate "Creation" to the UserService
        return ResponseEntity.ok(userService.register(request));
    }

    // ----------------------------------------------------------------
    // 2. AUTHENTICATION (Handled by AuthenticationService)
    // ----------------------------------------------------------------
    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(@Valid @RequestBody AuthenticationRequest request) {
        return ResponseEntity.ok(authenticationService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authenticationService.refreshToken(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        authenticationService.logout(authHeader);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/google")
    public ResponseEntity<AuthenticationResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authenticationService.googleLogin(request));
    }
}