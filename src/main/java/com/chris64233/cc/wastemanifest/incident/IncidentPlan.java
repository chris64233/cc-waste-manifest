package com.chris64233.cc.wastemanifest.incident;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 异常处置方案。由承运方针对一笔 OPEN 异常提出，产生方、承运方、原处置方按各自责任确认；
 * 改道时还需新处置方确认；涉及废物类别或数量变化时需监管决定。
 *
 * <p>方案只能基于异常登记时的当前有效版本（{@code baseVersionNo}）。确认期间任一责任方
 * 拒绝/撤回、联单版本被其他操作推进或监管冻结后来生效时，方案终止且不得留下半条运输链；
 * 全部必要确认齐备时在同一事务内一次性完成包装拆分并生效。</p>
 */
@Entity
@Table(name = "incident_plans",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "plan_no"),
                // 一笔异常同时只能有一个确认中方案，终态置空
                @UniqueConstraint(columnNames = "active_incident_id")
        })
public class IncidentPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_no", nullable = false, updatable = false, length = 64)
    private String planNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, updatable = false)
    private Incident incident;

    @Column(name = "base_version_no", nullable = false, updatable = false)
    private int baseVersionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PlanStatus status = PlanStatus.PENDING;

    /** 改道目标新处置方；泄漏/遗失为空 */
    @Column(name = "new_disposer_id", updatable = false, length = 64)
    private String newDisposerId;

    /** 改道预计到达时间；泄漏/遗失为空 */
    @Column(name = "estimated_arrival_at", updatable = false)
    private Instant estimatedArrivalAt;

    /** 是否涉及数量变化（受影响包装从原运输链核销），需监管决定 */
    @Column(name = "quantity_changed", nullable = false, updatable = false)
    private boolean quantityChanged;

    /** 是否涉及废物类别变化，需监管决定 */
    @Column(name = "category_changed", nullable = false, updatable = false)
    private boolean categoryChanged;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    /** PENDING 期间等于异常 id，终态置空 */
    @Column(name = "active_incident_id")
    private Long activeIncidentId;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<IncidentPlanDecision> decisions = new ArrayList<>();

    protected IncidentPlan() {
    }

    public IncidentPlan(String planNo, Incident incident, int baseVersionNo,
                        String newDisposerId, Instant estimatedArrivalAt,
                        boolean quantityChanged, boolean categoryChanged) {
        this.planNo = planNo;
        this.incident = incident;
        this.baseVersionNo = baseVersionNo;
        this.newDisposerId = newDisposerId;
        this.estimatedArrivalAt = estimatedArrivalAt;
        this.quantityChanged = quantityChanged;
        this.categoryChanged = categoryChanged;
        this.activeIncidentId = incident.getId();
    }

    public void addDecision(IncidentPlanDecision decision) {
        decisions.add(decision);
    }

    public boolean isPending() {
        return status == PlanStatus.PENDING;
    }

    public void markEffective() {
        this.status = PlanStatus.EFFECTIVE;
        this.closedAt = Instant.now();
        this.activeIncidentId = null;
    }

    public void markRejected() {
        this.status = PlanStatus.REJECTED;
        this.closedAt = Instant.now();
        this.activeIncidentId = null;
    }

    public void markWithdrawn() {
        this.status = PlanStatus.WITHDRAWN;
        this.closedAt = Instant.now();
        this.activeIncidentId = null;
    }

    public void markFrozen() {
        this.status = PlanStatus.FROZEN;
        this.closedAt = Instant.now();
        this.activeIncidentId = null;
    }

    public void markSuperseded() {
        this.status = PlanStatus.SUPERSEDED;
        this.closedAt = Instant.now();
        this.activeIncidentId = null;
    }

    public Long getId() {
        return id;
    }

    public String getPlanNo() {
        return planNo;
    }

    public Incident getIncident() {
        return incident;
    }

    public int getBaseVersionNo() {
        return baseVersionNo;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public String getNewDisposerId() {
        return newDisposerId;
    }

    public Instant getEstimatedArrivalAt() {
        return estimatedArrivalAt;
    }

    public boolean isQuantityChanged() {
        return quantityChanged;
    }

    public boolean isCategoryChanged() {
        return categoryChanged;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public List<IncidentPlanDecision> getDecisions() {
        return decisions;
    }
}
