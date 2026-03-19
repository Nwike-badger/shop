package semicolon.africa.waylchub.exception;

/**
 * Thrown when any payment gateway operation fails (auth, init, webhook verification).
 * Intentionally unchecked — callers decide whether to handle or bubble up.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}