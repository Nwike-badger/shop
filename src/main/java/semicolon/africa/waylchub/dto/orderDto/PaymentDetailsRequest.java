package semicolon.africa.waylchub.dto.orderDto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentDetailsRequest {
    @NotBlank(message = "Card number is required")
    @Pattern(regexp="^[0-9]{13,19}$", message="Invalid card number format")
    private String cardNumber;

    @NotBlank(message = "Card holder name is required")
    private String cardHolderName;

    @NotBlank(message = "Expiry month is required")
    @Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "Invalid expiry month (MM)")
    private String expiryMonth; // MM

    @NotBlank(message = "Expiry year is required")
    @Pattern(regexp = "^(20)\\d{2}$", message = "Invalid expiry year (YYYY)")
    private String expiryYear;

    @NotBlank(message = "CVV is required")
    @Pattern(regexp="^[0-9]{3,4}$", message="Invalid CVV format")
    private String cvv;
}
