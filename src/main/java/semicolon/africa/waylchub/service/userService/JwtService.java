package semicolon.africa.waylchub.service.userService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails;

public interface JwtService {
    String generateAccessToken(CustomUserDetails userDetails);

    String generateRefreshToken(CustomUserDetails userDetails);

    Jws<Claims> validateToken(String token);
}
