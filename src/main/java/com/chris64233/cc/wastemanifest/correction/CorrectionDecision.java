package com.chris64233.cc.wastemanifest.correction;

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

/**
 * 更正决定事件：产生方/承运方/处置方按各自涉及字段确认，监管方在类别变化时复核。
 * 决定事件号全局唯一，落库即不可修改。
 */
@Entity
@Table(name = "manifest_correction_decisions",
        uniqueConstraints = @UniqueConstraint(columnNames = "decision_no"))
public class CorrectionDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "decision_no", nullable = false, updatable = false, length = 64)
    private String decisionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "correction_id", nullable = false, updatable = false)
    private Correction correction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private DecisionRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_value", nullable = false, updatable = false, length = 16)
    private DecisionValue value;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();

    protected CorrectionDecision() {
    }

    public CorrectionDecision(String decisionNo, Correction correction, DecisionRole role,
                              DecisionValue value, Instant occurredAt) {
        this.decisionNo = decisionNo;
        this.correction = correction;
        this.role = role;
        this.value = value;
        this.occurredAt = occurredAt;
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException("更正决定事件不可修改: " + decisionNo);
    }

    /** 幂等匹配：同号同内容。 */
    public boolean matches(String correctionNo, DecisionRole expectedRole,
                           DecisionValue expectedValue, Instant expectedOccurredAt) {
        return correction.getCorrectionNo().equals(correctionNo)
                && role == expectedRole
                && value == expectedValue
                && occurredAt.equals(expectedOccurredAt);
    }

    public Long getId() {
        return id;
    }

    public String getDecisionNo() {
        return decisionNo;
    }

    public Correction getCorrection() {
        return correction;
    }

    public DecisionRole getRole() {
        return role;
    }

    public DecisionValue getValue() {
        return value;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
