package com.vdt.soc.notification.mail;

import com.vdt.soc.notification.client.TenantClient;
import com.vdt.soc.notification.dto.TenantContactResponse;
import com.vdt.soc.notification.entity.Alert;
import com.vdt.soc.notification.entity.AlertMailLog;
import com.vdt.soc.notification.repository.AlertMailLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Asynchronously sends an alert email to the tenant's registered contact
 * after the alert has been persisted.
 *
 * <p>SMTP send is fully decoupled from the JPA transaction that saves the
 * alert: mail failure never rolls back the alert, and Kafka keeps flowing.
 * Every attempt is recorded in {@code alert_mail_log} so deliverability can
 * be traced.
 */
@Slf4j
@Component
public class AlertMailer {

    private final TenantClient tenantClient;
    private final AlertMailLogRepository mailLogRepository;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public AlertMailer(TenantClient tenantClient, AlertMailLogRepository mailLogRepository) {
        this.tenantClient = tenantClient;
        this.mailLogRepository = mailLogRepository;
    }


    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${app.mail.enabled:true}")
    private boolean enabled;

    @Async("alertMailExecutor")
    @Transactional
    public void send(Alert alert) {
        if (!enabled) {
            log.warn("Alert mail is disabled (app.mail.enabled=false); skipping alert {} email", alert.getId());
            return;
        }

        if (mailSender == null) {
            log.warn("JavaMailSender is not available (spring.mail.host not configured); "
                    + "skipping alert {} email", alert.getId());
            return;
        }

        String recipient = null;
        try {
            TenantContactResponse tenant = tenantClient.getById(alert.getTenantId());
            if (tenant == null || !StringUtils.hasText(tenant.getEmail())) {
                log.warn("Tenant {} has no email configured; skipping alert {} email",
                        alert.getTenantId(), alert.getId());
                logStatus(alert, null, "SKIPPED", "No email configured");
                return;
            }
            recipient = tenant.getEmail();

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(StringUtils.hasText(fromAddress) ? fromAddress : "noreply@eps.local");
            helper.setTo(recipient);
            helper.setSubject(buildSubject(alert));
            helper.setText(buildBody(alert), false);

            mailSender.send(message);

            logStatus(alert, recipient, "SENT", null);
            log.info("Alert email sent to {} for alert {}", recipient, alert.getId());
        } catch (MailException | MessagingException ex) {
            log.error("Failed to send alert email to {} for alert {}", recipient, alert.getId(), ex);
            logStatus(alert, recipient, "FAILED", ex.getMessage());
        }
    }

    private void logStatus(Alert alert, String recipient, String status, String error) {
        AlertMailLog row = AlertMailLog.builder()
                .alertId(alert.getId())
                .recipient(recipient)
                .status(status)
                .error(error)
                .build();
        mailLogRepository.save(row);
    }

    private String buildSubject(Alert alert) {
        return String.format("[Alert] %s — %s (Severity: %s)",
                alert.getType(), alert.getTenantId(), alert.getSeverity());
    }

    private String buildBody(Alert alert) {
        StringBuilder sb = new StringBuilder();
        sb.append("An alert has been triggered for your tenant.").append("\n\n");
        sb.append("Alert Type: ").append(alert.getType()).append("\n");
        sb.append("Severity: ").append(alert.getSeverity()).append("\n");
        sb.append("Message: ").append(alert.getMessage()).append("\n");
        if (alert.getCurrentValue() != null) {
            sb.append("Current Value: ").append(alert.getCurrentValue()).append("\n");
        }
        if (alert.getThreshold() != null) {
            sb.append("Threshold: ").append(alert.getThreshold()).append("\n");
        }
        sb.append("Occurred At: ").append(alert.getCreatedAt()).append("\n\n");
        sb.append("Please review the platform dashboard for more details.");
        return sb.toString();
    }
}
