package semicolon.africa.waylchub.service.userService;

import semicolon.africa.waylchub.model.user.User;

public interface EmailVerificationService {
    void sendVerificationEmail(String email); // idempotent; no-op if already verified
    User verify(String rawToken);
}