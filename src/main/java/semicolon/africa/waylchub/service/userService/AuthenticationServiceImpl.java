package semicolon.africa.waylchub.service.userService;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.userDTO.*;
import semicolon.africa.waylchub.mapper.UserMapper;
import semicolon.africa.waylchub.model.user.*;
import semicolon.africa.waylchub.repository.userRepository.PasswordResetTokenRepository;
import semicolon.africa.waylchub.repository.userRepository.RoleRepository;
import semicolon.africa.waylchub.repository.userRepository.TokenRepository;
import semicolon.africa.waylchub.repository.userRepository.UserRepository;
import semicolon.africa.waylchub.service.emailService.EmailService;
import semicolon.africa.waylchub.service.productService.CartService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final CartService cartService;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    @Value("${spring.security.oauth2.client.registration.google.client-id:YOUR_GOOGLE_CLIENT_ID}")
    private String googleClientId;
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    @Transactional
    public AuthenticationResponse login(AuthenticationRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        CustomUserDetails userDetails = UserMapper.toCustomUserDetails(user);
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        revokeUserTokens(user, Arrays.asList(TokenType.ACCESS_TOKEN, TokenType.REFRESH_TOKEN));
        saveToken(user, accessToken, TokenType.ACCESS_TOKEN);
        saveToken(user, refreshToken, TokenType.REFRESH_TOKEN);

        if (request.getGuestId() != null && !request.getGuestId().isEmpty()) {
            try {
                cartService.mergeCarts(request.getGuestId(), user.getId());
            } catch (Exception e) {
                log.error("Failed to merge cart for user {}", user.getId(), e);
            }
        }

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .build();
    }

    // ── Google login ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthenticationResponse googleLogin(GoogleLoginRequest request) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
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
            String refreshToken = jwtService.generateRefreshToken(userDetails);

            revokeUserTokens(user, Arrays.asList(TokenType.ACCESS_TOKEN, TokenType.REFRESH_TOKEN));
            saveToken(user, accessToken, TokenType.ACCESS_TOKEN);
            saveToken(user, refreshToken, TokenType.REFRESH_TOKEN);

            if (request.getGuestId() != null && !request.getGuestId().isEmpty()) {
                try {
                    cartService.mergeCarts(request.getGuestId(), user.getId());
                } catch (Exception e) {
                    log.error("Failed to merge cart for Google user {}", user.getId(), e);
                }
            }

            return AuthenticationResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(3600L)
                    .build();

        } catch (Exception e) {
            log.error("Google authentication failed", e);
            throw new RuntimeException("Google authentication failed");
        }
    }

    // ── Refresh token ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthenticationResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        var claimsJws = jwtService.validateToken(refreshToken);

        Token dbToken = tokenRepository.findByTokenAndRevoked(refreshToken, false)
                .orElseThrow(() -> new RuntimeException("Invalid or revoked token"));

        if (dbToken.getTokenType() != TokenType.REFRESH_TOKEN) {
            throw new RuntimeException("Invalid token type. Refresh token required.");
        }

        String username = claimsJws.getBody().get("username", String.class);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        CustomUserDetails userDetails = UserMapper.toCustomUserDetails(user);
        String newAccessToken = jwtService.generateAccessToken(userDetails);

        revokeUserTokens(user, List.of(TokenType.ACCESS_TOKEN));
        saveToken(user, newAccessToken, TokenType.ACCESS_TOKEN);

        return AuthenticationResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .build();
    }

    // ── Logout ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return;

        String jwt = authHeader.substring(7);
        Token storedToken = tokenRepository.findByTokenAndRevoked(jwt, false).orElse(null);

        if (storedToken != null) {
            storedToken.setRevoked(true);
            tokenRepository.save(storedToken);
            revokeUserTokens(storedToken.getUser(),
                    Arrays.asList(TokenType.ACCESS_TOKEN, TokenType.REFRESH_TOKEN));
        }
    }

    // ── Forgot password ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // ── Security: always return 200 OK regardless of whether the email exists.
        // Returning a different response for unknown emails leaks user existence
        // (email enumeration attack).
        userRepository.findByEmail(email).ifPresent(user -> {

            // Invalidate any existing unused tokens for this email before issuing a new one
            passwordResetTokenRepository.findAllByEmailAndUsedFalse(email)
                    .forEach(t -> {
                        t.setUsed(true);
                        passwordResetTokenRepository.save(t);
                    });

            // 128 bits of entropy — effectively unguessable
            String rawToken = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "");

            // SHA-256 hash for storage — unlike BCrypt, this allows fast direct lookup
            // while still being a one-way function safe against DB dump attacks.
            // BCrypt is wrong here: it's deliberately slow and we need exact matching.
            String tokenHash = sha256(rawToken);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .tokenHash(tokenHash)
                    .userId(user.getId())
                    .email(email)
                    .expiresAt(LocalDateTime.now().plusMinutes(15))
                    .used(false)
                    .build();

            passwordResetTokenRepository.save(resetToken);

            // Resolve a proper display name for the email greeting
            String displayName = buildDisplayName(user.getFirstName(), user.getLastName());
            String resetLink = frontendUrl + "/reset-password?token=" + rawToken;

            // Fire-and-forget — @Async inside ResendEmailServiceImpl
            emailService.sendPasswordResetEmail(email, displayName, resetLink);
        });
    }

    // ── Reset password ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // Direct lookup by SHA-256 hash — O(1), no BCrypt scanning
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

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Mark token consumed — one-time use enforced
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Kick all active sessions — anyone holding old JWTs can no longer access the account
        revokeUserTokens(user, Arrays.asList(TokenType.ACCESS_TOKEN, TokenType.REFRESH_TOKEN));

        log.info("Password reset successful for userId={}", user.getId());
    }

    // ── Shared helpers ──────────────────────────────────────────────────────

    private void revokeUserTokens(User user, List<TokenType> tokenTypes) {
        tokenTypes.forEach(type -> {
            List<Token> tokens = tokenRepository.findAllByUserIdAndTokenType(user.getId(), type);
            tokens.forEach(token -> {
                token.setRevoked(true);
                tokenRepository.save(token);
            });
        });
    }

    private void saveToken(User user, String jwt, TokenType type) {
        tokenRepository.save(Token.builder()
                .token(jwt)
                .user(user)
                .tokenType(type)
                .revoked(false)
                .build());
    }

    /**
     * SHA-256 hash of a raw token string, Base64-encoded.
     * Used for password reset tokens — fast, one-way, safe to store.
     * BCrypt is deliberately slow (designed for passwords); it is wrong for tokens.
     */
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

