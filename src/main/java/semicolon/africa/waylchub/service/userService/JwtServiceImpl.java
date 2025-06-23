package semicolon.africa.waylchub.service.userService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails;
import semicolon.africa.waylchub.repository.userRepository.TokenRepository;

import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private final TokenRepository tokenRepository;

    private final String jwtSecret = "MbgaxjyxfGOeCdYIc4ibGIhA4NI69DvK";

    private final int accessTokenExpirationInSec = 3600; // 1 hour
    private final int refreshTokenExpirationInSec = 604800; // 7 days

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    @Override
    public String generateAccessToken(CustomUserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", userDetails.getId());
        claims.put("username", userDetails.getUsername());
        claims.put("firstName", userDetails.getFirstName());
        claims.put("lastName", userDetails.getLastName());
        claims.put("phoneNumber", userDetails.getPhoneNumber());
        claims.put("verified", userDetails.isVerified());
        claims.put("authorities", userDetails.getAuthorities());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(Date.from(LocalDateTime.now().plusSeconds(accessTokenExpirationInSec).toInstant(ZoneOffset.UTC)))
                .signWith(getSigningKey())
                .compact();
    }

    @Override
    public String generateRefreshToken(CustomUserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", userDetails.getId());
        claims.put("username", userDetails.getUsername());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(Date.from(LocalDateTime.now().plusSeconds(refreshTokenExpirationInSec).toInstant(ZoneOffset.UTC)))
                .signWith(getSigningKey())
                .compact();
    }

    @Override
    public Jws<Claims> validateToken(String token) {
        tokenRepository.findByTokenAndRevoked(token, false)
                .orElseThrow(() -> new JwtException("Invalid or revoked token"));

        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
    }
}
