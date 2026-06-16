package semicolon.africa.waylchub.service.userService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.model.user.EmailVerificationToken;
import semicolon.africa.waylchub.model.user.User;
import semicolon.africa.waylchub.repository.userRepository.EmailVerificationTokenRepository;
import semicolon.africa.waylchub.repository.userRepository.UserRepository;
import semicolon.africa.waylchub.service.emailService.EmailService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    @Transactional
    public void sendVerificationEmail(String email) {
        String normalized = email.toLowerCase().trim();

        // Silent for unknown emails (no enumeration). No-op if already verified.
        userRepository.findByEmail(normalized).ifPresent(user -> {
            if (user.isVerified()) return;

            // Invalidate any prior unused tokens for this email
            tokenRepository.findAllByEmailAndUsedFalse(normalized).forEach(t -> {
                t.setUsed(true);
                tokenRepository.save(t);
            });

            String rawToken = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "");
            String tokenHash = sha256(rawToken);

            EmailVerificationToken token = EmailVerificationToken.builder()
                    .tokenHash(tokenHash)
                    .userId(user.getId())
                    .email(normalized)
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .used(false)
                    .build();
            tokenRepository.save(token);

            String name = buildDisplayName(user.getFirstName(), user.getLastName());
            String verifyLink = frontendUrl + "/verify-email?token=" + rawToken;
            emailService.sendVerificationEmail(normalized, name, verifyLink);
        });
    }

    @Override
    @Transactional
    public User verify(String rawToken) {
        String tokenHash = sha256(rawToken);

        EmailVerificationToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new RuntimeException("Invalid or expired verification link"));

        if (token.isUsed()) {
            throw new RuntimeException("This verification link has already been used.");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("This verification link has expired. Please request a new one.");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setVerified(true);
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);

        log.info("Email verified for userId={}", user.getId());
        return user;   // ← return it
    }

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