package semicolon.africa.waylchub.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.userDTO.*;
import semicolon.africa.waylchub.exception.EmailNotVerifiedException;
import semicolon.africa.waylchub.exception.UserAlreadyExistsException;
import semicolon.africa.waylchub.model.user.User;
import semicolon.africa.waylchub.service.userService.AuthenticationService;
import semicolon.africa.waylchub.service.userService.EmailVerificationService;
import semicolon.africa.waylchub.service.userService.UserService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final UserService userService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody UserRegistrationRequest request) {
        try {
            UserResponse user = userService.register(request);
            // Fire the verification email (async). Account stays unverified until confirmed.
            emailVerificationService.sendVerificationEmail(request.getEmail());
            return ResponseEntity.ok(user);
        } catch (UserAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));  // "Email already in use"
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthenticationRequest request) {
        try {
            return ResponseEntity.ok(authenticationService.login(request));
        } catch (EmailNotVerifiedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "EMAIL_NOT_VERIFIED",
                    "message", e.getMessage(),
                    "email", request.getUsername()
            ));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "INVALID_CREDENTIALS",
                    "message", "Invalid email or password"
            ));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authenticationService.refreshToken(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authenticationService.logout(request.getHeader("Authorization"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/google")
    public ResponseEntity<AuthenticationResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authenticationService.googleLogin(request));
    }

    // ── Email verification ────────────────────────────────────────────────

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        try {
            User user = emailVerificationService.verify(request.getToken());
            // Verification succeeded — hand back a real session so the user lands logged in.
            AuthenticationResponse tokens = authenticationService.issueTokensForVerifiedUser(user);
            return ResponseEntity.ok(tokens);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.sendVerificationEmail(request.getEmail());
        // Always 200 — never reveal whether the email exists or is already verified
        return ResponseEntity.ok().build();
    }

    // ── Password reset ──────────────────────────────────────────────────────

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authenticationService.forgotPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            authenticationService.resetPassword(request);
            return ResponseEntity.ok(Map.of("message", "Password updated. You can now log in."));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}