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
 * 异常方案生效时一次性形成的运输去向：
 *
 * <ul>
 *   <li>泄漏：受影响包装（按处置口径重新包装/清理后）形成前往原处置方的新运输段；</li>
 *   <li>改道：受影响包装形成前往新处置方的新运输段；</li>
 *   <li>遗失：受影响包装形成损失记录（{@link SegmentType#LOSS}），退出运输链。</li>
 * </ul>
 *
 * 段与原联单相连（{@code parentManifest}），未受影响部分继续沿原联单流转，
 * 受影响部分只在本段内交接，任一失败时整次处置回滚、不产生任何段。
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
    @JoinColumn(name = "incident_id", nullable = false, updatable = false)
    private TransportIncident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manifest_id", nullable = false, updatable = false)
    private Manifest parentManifest;

    @Column(name = "seq_no", nullable = false, updatable = false)
    private int seqNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "segment_type", nullable = false, updatable = false, length = 16)
    private SegmentType type;

    /** 新运输段的目的处置方（泄漏为原处置方，改道为新处置方）；损失记录为空 */
    @Column(name = "destination_disposer_id", updatable = false, length = 64)
    private String destinationDisposerId;

    @Column(name = "estimated_arrival_at", updatable = false)
    private Instant estimatedArrivalAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SegmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_custodian", nullable = false, length = 16)
    private SegmentCustodian currentCustodian;

    @Column(name = "declared_weight", nullable = false, updatable = false, precision = 19, scale = 3)
    private java.math.BigDecimal declaredWeight;

    @Column(name = "received_weight", precision = 19, scale = 3)
    private java.math.BigDecimal receivedWeight;

    @Column(name = "effective_at", nullable = false, updatable = false)
    private Instant effectiveAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "segment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<SegmentItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "segment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<SegmentHandoverEvent> handoverEvents = new ArrayList<>();

    protected TransportSegment() {
    }

    public TransportSegment(String segmentNo, TransportIncident incident, Manifest parentManifest,
                            int seqNo, SegmentType type, String destinationDisposerId,
                            Instant estimatedArrivalAt, java.math.BigDecimal declaredWeight) {
        this.segmentNo = segmentNo;
        this.incident = incident;
        this.parentManifest = parentManifest;
        this.seqNo = seqNo;
        this.type = type;
        this.destinationDisposerId = destinationDisposerId;
        this.estimatedArrivalAt = estimatedArrivalAt;
        this.declaredWeight = declaredWeight;
        if (type == SegmentType.LOSS) {
            this.status = SegmentStatus.LOST;
            this.currentCustodian = SegmentCustodian.TRANSPORTER;
            this.completedAt = Instant.now();
        } else {
            this.status = SegmentStatus.IN_TRANSIT;
            this.currentCustodian = SegmentCustodian.TRANSPORTER;
        }
    }

    public void addItem(SegmentItem item) {
        items.add(item);
    }

    /** 段明细全部构建后一次性回填申报总重，仅在方案生效事务内调用。 */
    public void initDeclaredWeight(java.math.BigDecimal declaredWeight) {
        this.declaredWeight = declaredWeight;
    }

    public void addHandoverEvent(SegmentHandoverEvent event) {
        handoverEvents.add(event);
    }

    public void markDelivered() {
        this.status = SegmentStatus.DELIVERED;
    }

    public void markCompleted(java.math.BigDecimal receivedWeight) {
        this.status = SegmentStatus.COMPLETED;
        this.currentCustodian = SegmentCustodian.DISPOSER;
        this.receivedWeight = receivedWeight;
        this.completedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSegmentNo() {
        return segmentNo;
    }

    public TransportIncident getIncident() {
        return incident;
    }

    public Manifest getParentManifest() {
        return parentManifest;
    }

    public int getSeqNo() {
        return seqNo;
    }

    public SegmentType getType() {
        return type;
    }

    public String getDestinationDisposerId() {
        return destinationDisposerId;
    }

    public Instant getEstimatedArrivalAt() {
        return estimatedArrivalAt;
    }

    public SegmentStatus getStatus() {
        return status;
    }

    public SegmentCustodian getCurrentCustodian() {
        return currentCustodian;
    }

    public java.math.BigDecimal getDeclaredWeight() {
        return declaredWeight;
    }

    public java.math.BigDecimal getReceivedWeight() {
        return receivedWeight;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<SegmentItem> getItems() {
        return items;
    }

    public List<SegmentHandoverEvent> getHandoverEvents() {
        return handoverEvents;
    }
}
