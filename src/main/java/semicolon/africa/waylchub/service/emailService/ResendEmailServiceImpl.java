package semicolon.africa.waylchub.service.emailService;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
public class ResendEmailServiceImpl implements EmailService {

    private final Resend resend;
    private final String fromEmail;
    private final TemplateEngine templateEngine;

    public ResendEmailServiceImpl(
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from-email}") String fromEmail,
            TemplateEngine templateEngine) {
        this.resend = new Resend(apiKey);
        this.fromEmail = fromEmail;
        this.templateEngine = templateEngine;
    }

    @Override
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(to)
                    .subject(subject)
                    .html(htmlContent)
                    .build();

            CreateEmailResponse data = resend.emails().send(params);
            log.info("Email sent successfully to {} with ID: {}", to, data.getId());

        } catch (ResendException e) {
            log.error("Failed to send email to {}. Error: {}", to, e.getMessage(), e);
        }
    }

    @Override
    @Async
    public void sendPasswordResetEmail(String toEmail, String customerName, String resetLink) {
        try {
            Context context = new Context();
            context.setVariable("customerName", customerName);
            context.setVariable("resetLink", resetLink);

            String htmlContent = templateEngine.process("emails/password-reset", context);
            sendHtmlEmail(toEmail, "Reset your password", htmlContent);

        } catch (Exception e) {
            // Never propagate — a mail failure must not expose user existence or break flow
            log.error("Failed to send password reset email to {}. Error: {}", toEmail, e.getMessage(), e);
        }
    }
}