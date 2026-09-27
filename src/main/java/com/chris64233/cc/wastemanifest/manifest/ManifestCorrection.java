package com.chris64233.cc.wastemanifest.manifest;

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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "manifest_corrections",
        uniqueConstraints = @UniqueConstraint(columnNames = "correction_no"))
public class ManifestCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correction_no", nullable = false, updatable = false, length = 64)
    private String correctionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manifest_id", nullable = false, updatable = false)
    private Manifest manifest;

    @Column(name = "base_version_no", nullable = false, updatable = false)
    private int baseVersionNo;

    @Column(name = "result_version_no")
    private Integer resultVersionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CorrectionStatus status = CorrectionStatus.PENDING;

    @Column(nullable = false, updatable = false, length = 512)
    private String reason;

    @Column(nullable = false, updatable = false, length = 512)
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_impact", nullable = false, updatable = false, length = 40)
    private DisputeImpact disputeImpact;

    @Column(name = "required_roles", nullable = false, updatable = false, length = 64)
    private String requiredRoles;

    @OneToMany(mappedBy = "correction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CorrectionChange> changes = new ArrayList<>();

    @OneToMany(mappedBy = "correction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CorrectionDecision> decisions = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    protected ManifestCorrection() {
    }

    public ManifestCorrection(Manifest manifest, String correctionNo, int baseVersionNo,
                              String reason, String evidence, DisputeImpact disputeImpact,
                              List<ApproverRole> requiredRoles) {
        this.manifest = manifest;
        this.correctionNo = correctionNo;
        this.baseVersionNo = baseVersionNo;
        this.reason = reason;
        this.evidence = evidence;
        this.disputeImpact = disputeImpact;
        this.requiredRoles = requiredRoles.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    public void addChange(CorrectionChange change) {
        changes.add(change);
    }

    public void apply(int resultVersionNo) {
        this.status = CorrectionStatus.APPLIED;
        this.resultVersionNo = resultVersionNo;
        this.closedAt = Instant.now();
    }

    public void reject() {
        close(CorrectionStatus.REJECTED);
    }

    public void invalidate() {
        close(CorrectionStatus.INVALIDATED);
    }

    private void close(CorrectionStatus target) {
        this.status = target;
        this.closedAt = Instant.now();
    }

    public boolean requiresRole(ApproverRole role) {
        return getRequiredRoles().contains(role);
    }

    public List<ApproverRole> getRequiredRoles() {
        return Arrays.stream(requiredRoles.split(",")).map(ApproverRole::valueOf).toList();
    }

    public Long getId() {
        return id;
    }

    public String getCorrectionNo() {
        return correctionNo;
    }

    public Manifest getManifest() {
        return manifest;
    }

    public int getBaseVersionNo() {
        return baseVersionNo;
    }

    public Integer getResultVersionNo() {
        return resultVersionNo;
    }

    public CorrectionStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public String getEvidence() {
        return evidence;
    }

    public DisputeImpact getDisputeImpact() {
        return disputeImpact;
    }

    public List<CorrectionChange> getChanges() {
        return changes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
