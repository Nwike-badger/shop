package semicolon.africa.waylchub.service.emailService;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ResendEmailServiceImpl implements EmailService {

    private final Resend resend;
    private final String fromEmail;

    public ResendEmailServiceImpl(
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from-email}") String fromEmail) {
        this.resend = new Resend(apiKey);
        this.fromEmail = fromEmail;
    }

    @Override
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            // ✅ Updated from SendEmailRequest to CreateEmailOptions
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(to)
                    .subject(subject)
                    .html(htmlContent)
                    .build();

            // ✅ Updated from SendEmailResponse to CreateEmailResponse
            CreateEmailResponse data = resend.emails().send(params);
            log.info("Email sent successfully to {} with ID: {}", to, data.getId());

        } catch (ResendException e) {
            // Log the error but DO NOT throw it.
            // We don't want an email failure to crash the transaction.
            log.error("Failed to send email to {}. Error: {}", to, e.getMessage(), e);
        }
    }
}