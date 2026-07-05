package semicolon.africa.waylchub.service.userService;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.userDTO.*;
import semicolon.africa.waylchub.exception.EmailNotVerifiedException;
import semicolon.africa.waylchub.mapper.UserMapper;
import semicolon.africa.waylchub.model.user.*;
import semicolon.africa.waylchub.repository.userRepository.PasswordResetTokenRepository;
import semicolon.africa.waylchub.repository.userRepository.RefreshTokenRepository;
import semicolon.africa.waylchub.repository.userRepository.UserRepository;
import semicolon.africa.waylchub.service.emailService.EmailService;
import semicolon.africa.waylchub.service.productService.CartService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final CartService cartService;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;
    @Value("${app.google.client-id-web}")
    private String googleWebClientId;
    @Value("${app.google.client-id-ios:}")
    private String googleIosClientId;
    @Value("${app.google.client-id-android:}")
    private String googleAndroidClientId;
    @Value("${application.security.jwt.refresh-token-expiration:604800}")
    private long refreshTokenExpirationInSec;   // default 7 days

    // ── Login ─────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public AuthenticationResponse login(AuthenticationRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        // authenticate() already loaded the user — reuse the principal, no second DB lookup.
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        if (!userDetails.isVerified()) {
            throw new EmailNotVerifiedException("Please verify your email address before logging in.");
        }

        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = issueRefreshToken(userDetails.getUserId());

        if (request.getGuestId() != null && !request.getGuestId().isEmpty()) {
            try {
                cartService.mergeCarts(request.getGuestId(), userDetails.getUserId());
            } catch (Exception e) {
                log.error("Failed to merge cart for user {}", userDetails.getUserId(), e);
            }
        }

        return buildAuthResponse(accessToken, refreshToken);
    }

    // ── Post-verification auto-login ──────────────────────────────────────────
    @Override
    @Transactional
    public AuthenticationResponse issueTokensForVerifiedUser(User user) {
        CustomUserDetails userDetails = UserMapper.toCustomUserDetails(user);
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = issueRefreshToken(user.getId());
        return buildAuthResponse(accessToken, refreshToken);
    }

    // ── Google login ──────────────────────────────────────────────────────────
    @Override
    @Transactional
    public AuthenticationResponse googleLogin(GoogleLoginRequest request) {
        try {
            List<String> validAudiences = new ArrayList<>();
            validAudiences.add(googleWebClientId);
            if (!googleIosClientId.isBlank()) validAudiences.add(googleIosClientId);
            if (!googleAndroidClientId.isBlank()) validAudiences.add(googleAndroidClientId);

            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), new GsonFactory())
                    .setAudience(validAudiences)
                    .build();

            GoogleIdToken idToken = verifier.verify(request.getToken());
            if (idToken == null) {
                throw new RuntimeException("Invalid Google token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String firstName = (String) payload.get("given_name");
            String lastName = (String) payload.get("family_name");

            User user = userRepository.findByEmail(email).orElseGet(() -> {
                User newUser = User.builder()
                        .username(email)
                        .email(email)
                        .firstName(firstName)
                        .lastName(lastName != null ? lastName : "")
                        .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .roles(new HashSet<>(Collections.singleton(
                                roleService.getRoleByUserType(UserType.CUSTOMER))))
                        .enabled(true)
                        .accountNonExpired(true)
                        .accountNonLocked(true)
                        .credentialsNonExpired(true)
                        .verified(true)
                        .build();
                return userRepository.save(newUser);
            });

            CustomUserDetails userDetails = UserMapper.toCustomUserDetails(user);
            String accessToken = jwtService.generateAccessToken(userDetails);
            String refreshToken = issueRefreshToken(user.getId());

            if (request.getGuestId() != null && !request.getGuestId().isEmpty()) {
                try {
                    cartService.mergeCarts(request.getGuestId(), user.getId());
                } catch (Exception e) {
                    log.error("Failed to merge cart for Google user {}", user.getId(), e);
                }
            }

            return buildAuthResponse(accessToken, refreshToken);

        } catch (Exception e) {
            log.error("Google authentication failed", e);
            throw new RuntimeException("Google authentication failed");
        }
    }

    // ── Refresh (with rotation) ────────────────────────────────────────────────
    @Override
    @Transactional
    public AuthenticationResponse refreshToken(RefreshTokenRequest request) {
        String presented = request.getRefreshToken();
        if (presented == null || presented.isBlank()) {
            throw new RuntimeException("Refresh token is required");
        }

        String tokenHash = sha256(presented);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new RuntimeException("Invalid or expired refresh token"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(stored);
            throw new RuntimeException("Refresh token has expired. Please log in again.");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        CustomUserDetails userDetails = UserMapper.toCustomUserDetails(user);
        String newAccessToken = jwtService.generateAccessToken(userDetails);

        // Rotation: kill the presented token, hand back a fresh one.
        refreshTokenRepository.delete(stored);
        String newRefreshToken = issueRefreshToken(user.getId());

        return buildAuthResponse(newAccessToken, newRefreshToken);
    }

    // ── Logout (ends all sessions for the user) ─────────────────────────────────
    @Override
    @Transactional
    public void logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return;

        String accessToken = authHeader.substring(7);
        String userId;
        try {
            userId = jwtService.parseToken(accessToken).getBody().get("id", String.class);
        } catch (ExpiredJwtException e) {
            userId = e.getClaims().get("id", String.class);   // expired but still readable
        } catch (Exception e) {
            return;   // malformed/tampered — nothing to revoke
        }

        if (userId != null) {
            refreshTokenRepository.deleteAllByUserId(userId);
        }
    }

    // ── Forgot password ─────────────────────────────────────────────────────────
    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        userRepository.findByEmail(email).ifPresent(user -> {
            passwordResetTokenRepository.findAllByEmailAndUsedFalse(email)
                    .forEach(t -> { t.setUsed(true); passwordResetTokenRepository.save(t); });

            String rawToken = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "");
            String tokenHash = sha256(rawToken);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .tokenHash(tokenHash)
                    .userId(user.getId())
                    .email(email)
                    .expiresAt(LocalDateTime.now().plusMinutes(15))
                    .used(false)
                    .build();
            passwordResetTokenRepository.save(resetToken);

            String displayName = buildDisplayName(user.getFirstName(), user.getLastName());
            String resetLink = frontendUrl + "/reset-password?token=" + rawToken;
            emailService.sendPasswordResetEmail(email, displayName, resetLink);
        });
    }

    // ── Reset password ──────────────────────────────────────────────────────────
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = sha256(request.getToken());

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

        if (resetToken.isUsed()) {
            throw new RuntimeException("This reset link has already been used");
        }
        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("This reset link has expired. Please request a new one.");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Kick every session — old refresh tokens can no longer be used.
        refreshTokenRepository.deleteAllByUserId(user.getId());

        log.info("Password reset successful for userId={}", user.getId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String issueRefreshToken(String userId) {
        String rawToken = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(sha256(rawToken))
                .userId(userId)
                .createdAt(now)
                .expiresAt(now.plusSeconds(refreshTokenExpirationInSec))
                .build());
        return rawToken;   // raw goes to the client; only the hash is stored
    }

    private AuthenticationResponse buildAuthResponse(String accessToken, String refreshToken) {
        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenTtlSeconds())   // no longer a hardcoded lie
                .build();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available on this JVM", e);
        }
    }

    private String buildDisplayName(String firstName, String lastName) {
        String first = firstName != null ? firstName.trim() : "";
        String last = lastName != null ? lastName.trim() : "";
        if (!first.isEmpty() && !last.isEmpty()) return first + " " + last;
        if (!first.isEmpty()) return first;
        if (!last.isEmpty()) return last;
        return "Customer";
    }
}