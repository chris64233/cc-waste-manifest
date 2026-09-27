package com.chris64233.cc.wastemanifest.manifest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "correction_decisions",
        uniqueConstraints = @UniqueConstraint(columnNames = "decision_no"))
public class CorrectionDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "decision_no", nullable = false, updatable = false, length = 64)
    private String decisionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "correction_id", nullable = false, updatable = false)
    private ManifestCorrection correction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private ApproverRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private DecisionType decision;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();

    protected CorrectionDecision() {
    }

    public CorrectionDecision(ManifestCorrection correction, String decisionNo, ApproverRole role,
                              DecisionType decision, Instant occurredAt) {
        this.correction = correction;
        this.decisionNo = decisionNo;
        this.role = role;
        this.decision = decision;
        this.occurredAt = occurredAt;
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException("更正决定事件不可修改: " + decisionNo);
    }

    public boolean matches(String correctionNo, ApproverRole expectedRole,
                           DecisionType expectedDecision, Instant expectedOccurredAt) {
        return correction.getCorrectionNo().equals(correctionNo)
                && role == expectedRole
                && decision == expectedDecision
                && occurredAt.equals(expectedOccurredAt);
    }

    public Long getId() {
        return id;
    }

    public String getDecisionNo() {
        return decisionNo;
    }

    public ManifestCorrection getCorrection() {
        return correction;
    }

    public ApproverRole getRole() {
        return role;
    }

    public DecisionType getDecision() {
        return decision;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
