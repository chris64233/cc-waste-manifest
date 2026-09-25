package com.chris64233.cc.wastemanifest.manifest.domain;

import java.math.BigDecimal;
import java.time.Instant;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 不可变的交接 / 争议确认事件。eventNo 全局唯一，支持幂等重放。
 */
@Entity
@Table(name = "handover_event", uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_no", columnNames = "event_no"),
        @UniqueConstraint(name = "uk_event_manifest_seq", columnNames = {"manifest_id", "sequence"})
})
public class HandoverEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_no", nullable = false, length = 64)
    private String eventNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manifest_id", nullable = false)
    private WasteManifest manifest;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 24)
    private EventType eventType;

    @Column(name = "actor_party", nullable = false, length = 64)
    private String actorParty;

    @Column(name = "weight_value", nullable = false, precision = 12, scale = 3)
    private BigDecimal weightValue;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected HandoverEvent() {
    }

    public HandoverEvent(String eventNo, WasteManifest manifest, EventType eventType, String actorParty,
                         BigDecimal weightValue, Instant occurredAt, int sequence) {
        this.eventNo = eventNo;
        this.manifest = manifest;
        this.eventType = eventType;
        this.actorParty = actorParty;
        this.weightValue = weightValue;
        this.occurredAt = occurredAt;
        this.sequence = sequence;
    }

    @PrePersist
    void onCreate() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEventNo() {
        return eventNo;
    }

    public WasteManifest getManifest() {
        return manifest;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getActorParty() {
        return actorParty;
    }

    public BigDecimal getWeightValue() {
        return weightValue;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public int getSequence() {
        return sequence;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
