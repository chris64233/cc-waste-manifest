package com.chris64233.cc.wastemanifest.incident;

import com.chris64233.cc.wastemanifest.correction.DecisionValue;
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
 * 处置方案确认事件：各责任方按责任确认，事件号全局唯一、落库不可修改。
 * 监管决定同样以确认事件表达（角色为 REGULATOR）。
 */
@Entity
@Table(name = "incident_plan_decisions",
        uniqueConstraints = @UniqueConstraint(columnNames = "decision_no"))
public class IncidentPlanDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "decision_no", nullable = false, updatable = false, length = 64)
    private String decisionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private IncidentPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private PlanRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_value", nullable = false, updatable = false, length = 16)
    private DecisionValue value;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();

    protected IncidentPlanDecision() {
    }

    public IncidentPlanDecision(String decisionNo, IncidentPlan plan, PlanRole role,
                                DecisionValue value, Instant occurredAt) {
        this.decisionNo = decisionNo;
        this.plan = plan;
        this.value = value;
        this.occurredAt = occurredAt;
        this.role = role;
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException("处置方案决定事件不可修改: " + decisionNo);
    }

    public boolean matches(String planNo, PlanRole expectedRole, DecisionValue expectedValue,
                           Instant expectedOccurredAt) {
        return plan.getPlanNo().equals(planNo)
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

    public IncidentPlan getPlan() {
        return plan;
    }

    public PlanRole getRole() {
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
