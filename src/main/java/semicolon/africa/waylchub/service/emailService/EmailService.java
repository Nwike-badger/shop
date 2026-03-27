package semicolon.africa.waylchub.service.emailService;

public interface EmailService {
    void sendHtmlEmail(String to, String subject, String htmlContent);
    void sendPasswordResetEmail(String toEmail, String customerName, String resetLink);
}