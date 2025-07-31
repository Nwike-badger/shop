package semicolon.africa.waylchub.model.order;

public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED,
    CANCELLED // For payment that was initiated but cancelled
}
