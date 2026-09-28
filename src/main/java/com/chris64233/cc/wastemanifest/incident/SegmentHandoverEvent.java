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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 新运输段上的交接事件（承运方交付 / 处置方接收）。
 * 事件号全局唯一，落库不可修改；与原联单交接链一起构成完整交接链。
 */
@Entity
@Table(name = "segment_handover_events",
        uniqueConstraints = @UniqueConstraint(columnNames = "event_no"))
public class SegmentHandoverEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_no", nullable = false, updatable = false, length = 64)
    private String eventNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "segment_id", nullable = false, updatable = false)
    private TransportSegment segment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private SegmentEventType type;

    @Column(nullable = false, updatable = false, precision = 19, scale = 3)
    private BigDecimal weight;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();

    protected SegmentHandoverEvent() {
    }

    public SegmentHandoverEvent(String eventNo, TransportSegment segment, SegmentEventType type,
                                BigDecimal weight, Instant occurredAt) {
        this.eventNo = eventNo;
        this.segment = segment;
        this.type = type;
        this.weight = weight;
        this.occurredAt = occurredAt;
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException("运输段交接事件不可修改: " + eventNo);
    }

    public Long getId() {
        return id;
    }

    public String getEventNo() {
        return eventNo;
    }

    public TransportSegment getSegment() {
        return segment;
    }

    public SegmentEventType getType() {
        return type;
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
