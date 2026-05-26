package semicolon.africa.waylchub.dto.paymentDto;

import lombok.Data;

@Data
public class InitPaymentRequestBody {
    private String returnUrl;  // optional; mobile sends, web omits
}