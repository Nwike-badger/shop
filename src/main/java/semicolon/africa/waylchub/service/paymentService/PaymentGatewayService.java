package semicolon.africa.waylchub.service.paymentService;

import semicolon.africa.waylchub.dto.paymentDto.PaymentInitRequest;
import semicolon.africa.waylchub.dto.paymentDto.PaymentInitResponse;
import semicolon.africa.waylchub.dto.paymentDto.PaymentVerificationResult;

public interface PaymentGatewayService {
    PaymentInitResponse initializePayment(PaymentInitRequest request);
    boolean verifyWebhookSignature(String payload, String signature);
    PaymentVerificationResult verifyTransaction(String orderId);

}