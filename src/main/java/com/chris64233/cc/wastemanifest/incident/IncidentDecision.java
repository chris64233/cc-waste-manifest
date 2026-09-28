package com.chris64233.cc.wastemanifest.incident;

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
 * 异常处置决定事件：产生方、承运方、原处置方、（改道时）新处置方按各自责任确认，
 * 涉及废物类别或数量变化时监管方作出监管决定。决定事件号全局唯一，落库不可修改。
 */
@Entity
@Table(name = "incident_decisions",
        uniqueConstraints = @UniqueConstraint(columnNames = "decision_no"))
public class IncidentDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "decision_no", nullable = false, updatable = false, length = 64)
    private String decisionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, updatable = false)
    private TransportIncident incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private IncidentRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_value", nullable = false, updatable = false, length = 16)
    private IncidentDecisionValue value;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();

    protected IncidentDecision() {
    }

    public IncidentDecision(String decisionNo, TransportIncident incident, IncidentRole role,
                            IncidentDecisionValue value, Instant occurredAt) {
        this.decisionNo = decisionNo;
        this.incident = incident;
        this.role = role;
        this.value = value;
        this.occurredAt = occurredAt;
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException("异常处置决定事件不可修改: " + decisionNo);
    }

    /** 幂等匹配：同号同内容。 */
    public boolean matches(String incidentNo, IncidentRole expectedRole,
                           IncidentDecisionValue expectedValue, Instant expectedOccurredAt) {
        return incident.getIncidentNo().equals(incidentNo)
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

    public TransportIncident getIncident() {
        return incident;
    }

    public IncidentRole getRole() {
        return role;
    }

    public IncidentDecisionValue getValue() {
        return value;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
