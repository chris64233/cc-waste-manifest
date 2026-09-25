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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "manifest_events", uniqueConstraints = @UniqueConstraint(columnNames = "event_no"))
public class ManifestEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_no", nullable = false, updatable = false, length = 64)
    private String eventNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manifest_id", nullable = false, updatable = false)
    private Manifest manifest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private EventType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private CustodianRole role;

    @Column(nullable = false, updatable = false, precision = 19, scale = 3)
    private BigDecimal weight;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();

    protected ManifestEvent() {
    }

    public ManifestEvent(Manifest manifest, String eventNo, EventType type, CustodianRole role,
                         BigDecimal weight, Instant occurredAt) {
        this.manifest = manifest;
        this.eventNo = eventNo;
        this.type = type;
        this.role = role;
        this.weight = weight;
        this.occurredAt = occurredAt;
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException("联单事件不可修改: " + eventNo);
    }

    public boolean matches(String manifestNo, EventType expectedType, CustodianRole expectedRole,
                           BigDecimal expectedWeight, Instant expectedOccurredAt) {
        return manifest.getManifestNo().equals(manifestNo)
                && type == expectedType
                && role == expectedRole
                && weight.compareTo(expectedWeight) == 0
                && occurredAt.equals(expectedOccurredAt);
    }

    public Long getId() {
        return id;
    }

    public String getEventNo() {
        return eventNo;
    }

    public Manifest getManifest() {
        return manifest;
    }

    public EventType getType() {
        return type;
    }

    public CustodianRole getRole() {
        return role;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
