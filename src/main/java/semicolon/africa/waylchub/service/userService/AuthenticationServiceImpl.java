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
import semicolon.africa.waylchub.repository.userRepository.RoleRepository;
import semicolon.africa.waylchub.repository.userRepository.TokenRepository;
import semicolon.africa.waylchub.repository.userRepository.UserRepository;
import semicolon.africa.waylchub.service.productService.CartService;

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
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    @Value("${spring.security.oauth2.client.registration.google.client-id:YOUR_GOOGLE_CLIENT_ID}")
    private String googleClientId;

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
                // Don't fail login if cart merge fails, just log it
                log.error("Failed to merge cart for user {}", user.getId(), e);
            }
        }

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L) // TODO: Replace with properties value if configured
                .build();
    }

    @Override
    @Transactional
    public AuthenticationResponse googleLogin(GoogleLoginRequest request) {
        try {
            // 1. Verify Google Token securely
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
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

            // 2. Check if user exists, if not, create them!
            User user = userRepository.findByEmail(email).orElseGet(() -> {
                Role role = roleRepository.findByName(RoleName.ROLE_USER)
                        .orElseThrow(() -> new RuntimeException("Default role not found"));

                User newUser = User.builder()
                        .username(email)
                        .email(email)
                        .firstName(firstName)
                        .lastName(lastName != null ? lastName : "") // Google sometimes only has firstName
                        // Give them a random secure password since they use Google Auth
                        .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .roles(Collections.singleton(role))
                        .enabled(true)
                        .accountNonExpired(true)
                        .accountNonLocked(true)
                        .credentialsNonExpired(true)
                        .verified(true) // Google emails are already verified!
                        .build();

                return userRepository.save(newUser);
            });

            // 3. Generate YOUR App's JWTs
            CustomUserDetails userDetails = UserMapper.toCustomUserDetails(user);
            String accessToken = jwtService.generateAccessToken(userDetails);
            String refreshToken = jwtService.generateRefreshToken(userDetails);

            revokeUserTokens(user, Arrays.asList(TokenType.ACCESS_TOKEN, TokenType.REFRESH_TOKEN));
            saveToken(user, accessToken, TokenType.ACCESS_TOKEN);
            saveToken(user, refreshToken, TokenType.REFRESH_TOKEN);

            // 4. Merge Cart just like standard login
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

    @Override
    @Transactional
    public AuthenticationResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        // 1. Validate signature
        var claimsJws = jwtService.validateToken(refreshToken);

        // 2. CHECK THE DATABASE FOR THE ACTUAL TOKEN TYPE
        Token dbToken = tokenRepository.findByTokenAndRevoked(refreshToken, false)
                .orElseThrow(() -> new RuntimeException("Invalid or revoked token"));

        // 3. THE SECURITY LOCK
        if (dbToken.getTokenType() != TokenType.REFRESH_TOKEN) {
            throw new RuntimeException("Invalid token type. Refresh token required.");
        }

        String username = claimsJws.getBody().get("username", String.class);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        CustomUserDetails userDetails = UserMapper.toCustomUserDetails(user);
        String newAccessToken = jwtService.generateAccessToken(userDetails);

        // Only revoke old access tokens, keep the refresh token alive
        revokeUserTokens(user, List.of(TokenType.ACCESS_TOKEN));
        saveToken(user, newAccessToken, TokenType.ACCESS_TOKEN);

        return AuthenticationResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .build();
    }

    @Override
    @Transactional
    public void logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return; // No token provided, nothing to log out
        }

        String jwt = authHeader.substring(7);
        Token storedToken = tokenRepository.findByTokenAndRevoked(jwt, false).orElse(null);

        if (storedToken != null) {
            // Revoke the specific token used to make the request
            storedToken.setRevoked(true);
            tokenRepository.save(storedToken);

            // Optional: Revoke ALL tokens for this user for maximum security
            User user = storedToken.getUser();
            revokeUserTokens(user, Arrays.asList(TokenType.ACCESS_TOKEN, TokenType.REFRESH_TOKEN));
        }
    }

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
        Token token = Token.builder()
                .token(jwt)
                .user(user)
                .tokenType(type)
                .revoked(false)
                .build();

        tokenRepository.save(token);
    }
}

