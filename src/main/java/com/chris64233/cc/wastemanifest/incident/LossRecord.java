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
 * 泄漏/遗失受影响包装形成的损失记录，与原联单相连。
 */
@Entity
@Table(name = "incident_loss_records",
        uniqueConstraints = @UniqueConstraint(columnNames = "loss_no"))
public class LossRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loss_no", nullable = false, updatable = false, length = 64)
    private String lossNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manifest_id", nullable = false, updatable = false)
    private com.chris64233.cc.wastemanifest.manifest.Manifest manifest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, updatable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private IncidentPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "loss_type", nullable = false, updatable = false, length = 16)
    private IncidentType lossType;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();

    protected LossRecord() {
    }

    public LossRecord(String lossNo, com.chris64233.cc.wastemanifest.manifest.Manifest manifest,
                      Incident incident, IncidentPlan plan, IncidentType lossType) {
        this.lossNo = lossNo;
        this.manifest = manifest;
        this.incident = incident;
        this.plan = plan;
        this.lossType = lossType;
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException("损失记录不可修改: " + lossNo);
    }

    public Long getId() {
        return id;
    }

    public String getLossNo() {
        return lossNo;
    }

    public com.chris64233.cc.wastemanifest.manifest.Manifest getManifest() {
        return manifest;
    }

    public Incident getIncident() {
        return incident;
    }

    public IncidentPlan getPlan() {
        return plan;
    }

    public IncidentType getLossType() {
        return lossType;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
