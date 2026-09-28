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

import com.chris64233.cc.wastemanifest.manifest.Manifest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 改道产生的新运输段：与原联单相连，承载受影响包装运往新处置方。
 * 未受影响包装仍沿原联单流转，不在本段内。
 */
@Entity
@Table(name = "transport_segments",
        uniqueConstraints = @UniqueConstraint(columnNames = "segment_no"))
public class TransportSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "segment_no", nullable = false, updatable = false, length = 64)
    private String segmentNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manifest_id", nullable = false, updatable = false)
    private Manifest manifest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, updatable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private IncidentPlan plan;

    @Column(name = "new_disposer_id", nullable = false, updatable = false, length = 64)
    private String newDisposerId;

    @Column(name = "estimated_arrival_at", nullable = false, updatable = false)
    private Instant estimatedArrivalAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SegmentStatus status = SegmentStatus.IN_TRANSIT;

    @Column(name = "effective_at", nullable = false, updatable = false)
    private Instant effectiveAt = Instant.now();

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @OneToMany(mappedBy = "segment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<IncidentPackageSplit> packages = new ArrayList<>();

    protected TransportSegment() {
    }

    public TransportSegment(String segmentNo, Manifest manifest, Incident incident, IncidentPlan plan,
                            String newDisposerId, Instant estimatedArrivalAt) {
        this.segmentNo = segmentNo;
        this.manifest = manifest;
        this.incident = incident;
        this.plan = plan;
        this.newDisposerId = newDisposerId;
        this.estimatedArrivalAt = estimatedArrivalAt;
    }

    public void addPackage(IncidentPackageSplit split) {
        packages.add(split);
    }

    public void markDelivered() {
        this.status = SegmentStatus.DELIVERED;
        this.deliveredAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSegmentNo() {
        return segmentNo;
    }

    public Manifest getManifest() {
        return manifest;
    }

    public Incident getIncident() {
        return incident;
    }

    public IncidentPlan getPlan() {
        return plan;
    }

    public String getNewDisposerId() {
        return newDisposerId;
    }

    public Instant getEstimatedArrivalAt() {
        return estimatedArrivalAt;
    }

    public SegmentStatus getStatus() {
        return status;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public List<IncidentPackageSplit> getPackages() {
        return packages;
    }
}
