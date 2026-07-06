package com.vdt.soc.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One row per alert-email send attempt, written by {@code AlertMailer}.
 * Status is either {@code SENT} (SMTP accepted) or {@code FAILED} (exception
 * thrown, error captured). Used to answer "did this alert actually email out?"
 */
@Entity
@Table(name = "alert_mail_log", schema = "notify_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertMailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Column(length = 255)
    private String recipient;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String error;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;
}
