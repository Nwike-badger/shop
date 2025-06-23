package semicolon.africa.waylchub.service.userService;

import lombok.RequiredArgsConstructor;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;

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
            Optional<Token> token = tokenRepository.findByUserIdAndTokenType(user.getId(), type);
            token.ifPresent(t -> {
                t.setRevoked(true);
                tokenRepository.save(t);
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

