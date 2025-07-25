package semicolon.africa.waylchub.repository.userRepository;

import org.springframework.data.mongodb.repository.MongoRepository;
import semicolon.africa.waylchub.model.user.Token;
import semicolon.africa.waylchub.model.user.TokenType;

import java.util.List;
import java.util.Optional;

public interface TokenRepository extends MongoRepository<Token, String> {
    List<Token> findAllByUserIdAndTokenType(String userId, TokenType tokenType);


    Optional<Token> findByTokenAndRevoked(String token, boolean revoked);
}
