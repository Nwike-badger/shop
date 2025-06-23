package semicolon.africa.waylchub.model.user;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import semicolon.africa.waylchub.model.user.TokenType;
import semicolon.africa.waylchub.model.user.User;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tokens")
public class Token {

    @Id
    private String id;

    private String token;

    private TokenType tokenType;

    private boolean revoked;
    private User user;

//    private String userId;
}

