package semicolon.africa.waylchub.service.userService;

import semicolon.africa.waylchub.dto.userDTO.AuthenticationRequest;
import semicolon.africa.waylchub.dto.userDTO.AuthenticationResponse;
import semicolon.africa.waylchub.dto.userDTO.RefreshTokenRequest;

public interface AuthenticationService {
    AuthenticationResponse login(AuthenticationRequest request);
    AuthenticationResponse refreshToken(RefreshTokenRequest request);
    void logout(String username);
}
