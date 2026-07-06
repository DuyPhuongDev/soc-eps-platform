package com.vdt.soc.notification.mail;

import com.vdt.soc.common.core.enumeration.AlertType;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
    private final SpringTemplateEngine templateEngine;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public AlertMailer(TenantClient tenantClient, AlertMailLogRepository mailLogRepository, SpringTemplateEngine templateEngine) {
        this.tenantClient = tenantClient;
        this.mailLogRepository = mailLogRepository;
        this.templateEngine = templateEngine;
    }

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${app.mail.enabled:true}")
    private boolean enabled;

    @Async("alertMailExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(Alert alert, Map<String, Object> metaData) {
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

            // Tạo và gửi Email HTML dạng MimeMessage
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(StringUtils.hasText(fromAddress) ? fromAddress : "noreply@eps.local");
            helper.setTo(recipient);
            helper.setSubject(buildSubject(alert));


            String htmlBody = buildHtmlBody(alert, metaData);
            helper.setText(htmlBody, true);

            mailSender.send(message);

            logStatus(alert, recipient, "SENT", null);
            log.info("Alert email sent to {} for alert {}", recipient, alert.getId());
        } catch (MailException | MessagingException ex) {
            log.error("Failed to send alert email to {} for alert {}", recipient, alert.getId(), ex);
            logStatus(alert, recipient, "FAILED", ex.getMessage());
        } catch (Exception ex) {
            log.error("Error generating template or processing email logic for alert {}", alert.getId(), ex);
            logStatus(alert, recipient, "FAILED", "Template error: " + ex.getMessage());
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
        if (AlertType.LICENSE_EXPIRING.equals(alert.getType())) {
            return "[Cảnh báo] Bản quyền dịch vụ sắp hết hạn — Viettel SOC Platform";
        }
        return String.format("[Cảnh báo] %s — Hệ thống giám sát lưu lượng Viettel SOC", alert.getType());
    }

    /**
     * Hàm dựng HTML Context động dựa theo loại Alert và Metadata đi kèm
     */
    private String buildHtmlBody(Alert alert, Map<String, Object> metaData) {
        Context ctx = new Context(Locale.forLanguageTag("vi"));

        if (metaData != null) {
            metaData.forEach(ctx::setVariable);
        }

        ctx.setVariable("tenantId", alert.getTenantId() != null ? alert.getTenantId().toString() : ctx.getVariable("tenantId"));

        if (AlertType.LICENSE_EXPIRING.equals(alert.getType())) {

            if (ctx.getVariable("plan") == null) ctx.setVariable("plan", "STARTER");
            if (ctx.getVariable("reminderStr") == null) ctx.setVariable("reminderStr", "7 ngày");
            if (ctx.getVariable("endDateStr") == null) ctx.setVariable("endDateStr", "Liên hệ Admin");

            return templateEngine.process("mail/reminder-license", ctx);
        }

        else {
            double currentValue = alert.getCurrentValue() != null ? alert.getCurrentValue() : 0.0;
            double thresholdValue = alert.getThreshold() != null ? alert.getThreshold() : 1.0;

            if (ctx.getVariable("threshold") == null) {
                long thresholdPercent = Math.round((currentValue / thresholdValue) * 100);
                ctx.setVariable("threshold", thresholdPercent == 0 ? 70 : thresholdPercent);
            }

            ctx.setVariable("epsQuota", (int) thresholdValue);
            ctx.setVariable("currentEps", (int) currentValue);
            ctx.setVariable("message", alert.getMessage() != null ? alert.getMessage() : ctx.getVariable("message"));

            if (ctx.getVariable("mode") == null) ctx.setVariable("mode", "THROTTLE");
            return templateEngine.process("mail/alert-eps", ctx);
        }
    }
}