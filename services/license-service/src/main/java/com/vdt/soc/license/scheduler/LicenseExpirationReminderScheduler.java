package com.vdt.soc.license.scheduler;

import com.vdt.soc.license.entity.License;
import com.vdt.soc.license.event.LicenseEventPublisher;
import com.vdt.soc.license.repository.LicenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LicenseExpirationReminderScheduler {
    private final LicenseRepository licenseRepository;
    private final LicenseEventPublisher licenseEventPublisher;

    @Value("${license.events.license-reminder-days-before:7}")
    private long daysBefore;

    @Value("${license.events.license-reminder-window-minutes:1440}")
    private long windowMinutes;

    @Scheduled(cron = "${license.events.license-reminder-cron:0 0 12 * * *}")
    @Transactional(readOnly = true)
    public void publishExpirationReminders() {

        if (daysBefore <= 0 || windowMinutes <= 0) {
            return;
        }

        Instant now = Instant.now();
        Instant from = now.plus(daysBefore, ChronoUnit.DAYS);
        Instant to = from.plus(windowMinutes, ChronoUnit.MINUTES);

        log.info("Starting to schedule expiration reminders for licenses from {} to {}", from, to);
        List<License> dueLicenses = licenseRepository.findLicensesExpiringSoon(from, to);

        if (dueLicenses.isEmpty()) {
            return;
        }

        for (License license : dueLicenses) {
            long dayToExpiration = Duration.between(now, license.getEndDate()).toDays();
            licenseEventPublisher.publishDeadlineReminder(license, dayToExpiration);
        }

        log.info("Published {} expiration reminder event(s) for window {} -> {}", dueLicenses.size(), from, to);
    }
}
