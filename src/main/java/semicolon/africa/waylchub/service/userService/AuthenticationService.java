package semicolon.africa.waylchub.service.userService;

import semicolon.africa.waylchub.dto.userDTO.*;

public interface AuthenticationService {
    AuthenticationResponse login(AuthenticationRequest request);
    AuthenticationResponse refreshToken(RefreshTokenRequest request);
    AuthenticationResponse googleLogin(GoogleLoginRequest request);
    void logout(String authHeader);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
}
