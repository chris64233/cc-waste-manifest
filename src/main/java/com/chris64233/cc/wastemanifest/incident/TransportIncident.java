package com.chris64233.cc.wastemanifest.incident;

import com.chris64233.cc.wastemanifest.manifest.Manifest;
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
 * 运输异常处置。承运方针对运输中（或交付时发现）的当前有效联单登记，
 * 记录唯一事件号、发生时间、地点、受影响包装及数量、异常类型和证据。
 *
 * <p>处置只能引用登记时的当前有效版本（{@code baseVersionNo}）；确认期间若该版本
 * 被其他操作（差错更正）推进、联单被监管冻结或任一责任方拒绝，本次处置终止，
 * 不得留下半条运输链。</p>
 */
@Entity
@Table(name = "transport_incidents",
        uniqueConstraints = @UniqueConstraint(columnNames = "incident_no"))
public class TransportIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_no", nullable = false, updatable = false, length = 64)
    private String incidentNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manifest_id", nullable = false, updatable = false)
    private Manifest manifest;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false, updatable = false, length = 16)
    private IncidentType type;

    /** 登记时引用的联单当前有效版本号 */
    @Column(name = "base_version_no", nullable = false, updatable = false)
    private int baseVersionNo;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(nullable = false, updatable = false, length = 256)
    private String location;

    @Column(name = "evidence_ref", nullable = false, updatable = false, length = 500)
    private String evidenceRef;

    /** 改道时的新处置方；泄漏/遗失时为空 */
    @Column(name = "new_disposer_id", updatable = false, length = 64)
    private String newDisposerId;

    /** 改道时的预计到达时间 */
    @Column(name = "estimated_arrival_at", updatable = false)
    private Instant estimatedArrivalAt;

    /** 方案是否涉及废物类别或数量变化，涉及则监管决定为必要决定 */
    @Column(name = "regulator_required", nullable = false, updatable = false)
    private boolean regulatorRequired;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentStatus status = IncidentStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<IncidentAffectedLine> affectedLines = new ArrayList<>();

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<IncidentDecision> decisions = new ArrayList<>();

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<TransportSegment> segments = new ArrayList<>();

    protected TransportIncident() {
    }

    public TransportIncident(String incidentNo, Manifest manifest, IncidentType type,
                             int baseVersionNo, Instant occurredAt, String location,
                             String evidenceRef, String newDisposerId,
                             Instant estimatedArrivalAt, boolean regulatorRequired) {
        this.incidentNo = incidentNo;
        this.manifest = manifest;
        this.type = type;
        this.baseVersionNo = baseVersionNo;
        this.occurredAt = occurredAt;
        this.location = location;
        this.evidenceRef = evidenceRef;
        this.newDisposerId = newDisposerId;
        this.estimatedArrivalAt = estimatedArrivalAt;
        this.regulatorRequired = regulatorRequired;
    }

    public void addAffectedLine(IncidentAffectedLine line) {
        affectedLines.add(line);
    }

    public void addDecision(IncidentDecision decision) {
        decisions.add(decision);
    }

    public void addSegment(TransportSegment segment) {
        segments.add(segment);
    }

    public boolean isPending() {
        return status == IncidentStatus.PENDING;
    }

    public boolean isEffective() {
        return status == IncidentStatus.EFFECTIVE;
    }

    public void markEffective() {
        this.status = IncidentStatus.EFFECTIVE;
        this.closedAt = Instant.now();
    }

    public void markRejected() {
        this.status = IncidentStatus.REJECTED;
        this.closedAt = Instant.now();
    }

    public void markWithdrawn() {
        this.status = IncidentStatus.WITHDRAWN;
        this.closedAt = Instant.now();
    }

    public void markFrozen() {
        this.status = IncidentStatus.FROZEN;
        this.closedAt = Instant.now();
    }

    public void markSuperseded() {
        this.status = IncidentStatus.SUPERSEDED;
        this.closedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getIncidentNo() {
        return incidentNo;
    }

    public Manifest getManifest() {
        return manifest;
    }

    public IncidentType getType() {
        return type;
    }

    public int getBaseVersionNo() {
        return baseVersionNo;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getLocation() {
        return location;
    }

    public String getEvidenceRef() {
        return evidenceRef;
    }

    public String getNewDisposerId() {
        return newDisposerId;
    }

    public Instant getEstimatedArrivalAt() {
        return estimatedArrivalAt;
    }

    public boolean isRegulatorRequired() {
        return regulatorRequired;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public List<IncidentAffectedLine> getAffectedLines() {
        return affectedLines;
    }

    public List<IncidentDecision> getDecisions() {
        return decisions;
    }

    public List<TransportSegment> getSegments() {
        return segments;
    }
}
