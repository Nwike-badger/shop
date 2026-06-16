package semicolon.africa.waylchub.repository.userRepository;

import org.springframework.data.mongodb.repository.MongoRepository;
import semicolon.africa.waylchub.model.user.EmailVerificationToken;

import java.util.List;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends MongoRepository<EmailVerificationToken, String> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
    List<EmailVerificationToken> findAllByEmailAndUsedFalse(String email);
}