package com.chris64233.cc.wastemanifest.correction;

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

import com.chris64233.cc.wastemanifest.manifest.Manifest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 差错更正申请。已完成联单不可直接编辑，所有修改均以更正申请驱动。
 *
 * <p>申请基于申请时的当前有效版本（{@code baseVersionNo}）；确认期间若该版本被其他
 * 更正取代、联单被监管冻结或参与方撤回，本申请不得落地。</p>
 */
@Entity
@Table(name = "manifest_corrections",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "correction_no"),
                // 活动（PENDING）更正守卫：终态时置空，多个 NULL 不冲突
                @UniqueConstraint(columnNames = "active_manifest_no")
        })
public class Correction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correction_no", nullable = false, updatable = false, length = 64)
    private String correctionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manifest_id", nullable = false, updatable = false)
    private Manifest manifest;

    /** 申请所基于的版本号 */
    @Column(name = "base_version_no", nullable = false, updatable = false)
    private int baseVersionNo;

    /** 生效后产生的版本号；确认中为空 */
    @Column(name = "result_version_no")
    private Integer resultVersionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CorrectionStatus status = CorrectionStatus.PENDING;

    @Column(name = "applicant_role", nullable = false, updatable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private DecisionRole applicantRole;

    @Column(name = "reason", nullable = false, updatable = false, length = 1000)
    private String reason;

    @Column(name = "evidence_ref", updatable = false, length = 500)
    private String evidenceRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_impact", nullable = false, updatable = false, length = 48)
    private DisputeImpact disputeImpact;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    /** PENDING 期间等于联单号（数据库唯一约束保证一联单至多一笔活动更正），终态置空 */
    @Column(name = "active_manifest_no", length = 64)
    private String activeManifestNo;

    @OneToMany(mappedBy = "correction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CorrectionChange> changes = new ArrayList<>();

    @OneToMany(mappedBy = "correction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CorrectionDecision> decisions = new ArrayList<>();

    protected Correction() {
    }

    public Correction(String correctionNo, Manifest manifest, int baseVersionNo,
                      DecisionRole applicantRole, String reason, String evidenceRef,
                      DisputeImpact disputeImpact) {
        this.correctionNo = correctionNo;
        this.manifest = manifest;
        this.baseVersionNo = baseVersionNo;
        this.applicantRole = applicantRole;
        this.reason = reason;
        this.evidenceRef = evidenceRef;
        this.disputeImpact = disputeImpact;
        this.activeManifestNo = manifest.getManifestNo();
    }

    public void addChange(CorrectionChange change) {
        changes.add(change);
    }

    public void addDecision(CorrectionDecision decision) {
        decisions.add(decision);
    }

    public boolean isPending() {
        return status == CorrectionStatus.PENDING;
    }

    public void markEffective(int resultVersionNo) {
        this.status = CorrectionStatus.EFFECTIVE;
        this.resultVersionNo = resultVersionNo;
        this.closedAt = Instant.now();
        this.activeManifestNo = null;
    }

    public void markRejected() {
        this.status = CorrectionStatus.REJECTED;
        this.closedAt = Instant.now();
        this.activeManifestNo = null;
    }

    public void markWithdrawn() {
        this.status = CorrectionStatus.WITHDRAWN;
        this.closedAt = Instant.now();
        this.activeManifestNo = null;
    }

    public void markFrozen() {
        this.status = CorrectionStatus.FROZEN;
        this.closedAt = Instant.now();
        this.activeManifestNo = null;
    }

    public void markSuperseded() {
        this.status = CorrectionStatus.SUPERSEDED;
        this.closedAt = Instant.now();
        this.activeManifestNo = null;
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

    public DecisionRole getApplicantRole() {
        return applicantRole;
    }

    public String getReason() {
        return reason;
    }

    public String getEvidenceRef() {
        return evidenceRef;
    }

    public DisputeImpact getDisputeImpact() {
        return disputeImpact;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public List<CorrectionChange> getChanges() {
        return changes;
    }

    public List<CorrectionDecision> getDecisions() {
        return decisions;
    }
}
