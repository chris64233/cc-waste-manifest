package com.chris64233.cc.wastemanifest.incident;

import com.chris64233.cc.wastemanifest.manifest.CustodianRole;
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
 * 新运输段交接事件：承运方发运 → 新处置方收货。事件号全局唯一，落库不可修改。
 */
@Entity
@Table(name = "segment_events", uniqueConstraints = @UniqueConstraint(columnNames = "event_no"))
public class SegmentEvent {

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
    private CustodianRole role;

    @Column(nullable = false, updatable = false, precision = 19, scale = 3)
    private BigDecimal weight;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();

    protected SegmentEvent() {
    }

    public SegmentEvent(String eventNo, TransportSegment segment, CustodianRole role,
                        BigDecimal weight, Instant occurredAt) {
        this.eventNo = eventNo;
        this.segment = segment;
        this.role = role;
        this.weight = weight;
        this.occurredAt = occurredAt;
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException("新运输段事件不可修改: " + eventNo);
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
