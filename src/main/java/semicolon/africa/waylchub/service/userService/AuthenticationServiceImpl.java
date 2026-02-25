package semicolon.africa.waylchub.service.userService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.userDTO.AuthenticationRequest;
import semicolon.africa.waylchub.dto.userDTO.AuthenticationResponse;
import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails;
import semicolon.africa.waylchub.dto.userDTO.RefreshTokenRequest;
import semicolon.africa.waylchub.mapper.UserMapper;
import semicolon.africa.waylchub.model.user.Token;
import semicolon.africa.waylchub.model.user.TokenType;
import semicolon.africa.waylchub.model.user.User;
import semicolon.africa.waylchub.repository.userRepository.TokenRepository;
import semicolon.africa.waylchub.repository.userRepository.UserRepository;
import semicolon.africa.waylchub.service.productService.CartService;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final CartService cartService;

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
    public AuthenticationResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        var claimsJws = jwtService.validateToken(refreshToken);
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

    @Override
    @Transactional
    public void logout(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        revokeUserTokens(user, Arrays.asList(TokenType.ACCESS_TOKEN, TokenType.REFRESH_TOKEN));
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

