package semicolon.africa.waylchub.service.userService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JwtServiceImpl implements JwtService {

    @Value("${application.security.jwt.secret-key}")
    private String jwtSecret;

    @Value("${application.security.jwt.access-token-expiration:900}")
    private long accessTokenExpirationInSec;   // default 15 minutes

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String generateAccessToken(CustomUserDetails userDetails) {
        List<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        Map<String, Object> claims = new HashMap<>();
        claims.put("id", userDetails.getUserId());
        claims.put("username", userDetails.getUsername());
        claims.put("firstName", userDetails.getFirstName());
        claims.put("lastName", userDetails.getLastName());
        claims.put("phoneNumber", userDetails.getPhoneNumber());
        claims.put("verified", userDetails.isVerified());
        claims.put("authorities", authorities);   // plain ["ROLE_USER","ROLE_ADMIN"]

        Instant now = Instant.now();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(accessTokenExpirationInSec)))
                .signWith(getSigningKey())
                .compact();
    }

    @Override
    public Jws<Claims> parseToken(String token) {
        // Runs on every request — signature + expiry only, no database hit.
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
    }

    @Override
    public long getAccessTokenTtlSeconds() {
        return accessTokenExpirationInSec;
    }
}