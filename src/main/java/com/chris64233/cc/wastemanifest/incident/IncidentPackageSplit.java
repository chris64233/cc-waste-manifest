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

/**
 * 包装拆分行：方案生效时一次性记录每个受影响明细的拆分前后关系。
 * 受影响数量进入新运输段（SEGMENT）或损失记录（LOSS）；未受影响数量继续沿原联单流转。
 */
@Entity
@Table(name = "incident_package_splits")
public class IncidentPackageSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private IncidentPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, updatable = false)
    private Incident incident;

    @Column(name = "item_seq", nullable = false, updatable = false)
    private int itemSeq;

    @Column(name = "waste_category", nullable = false, updatable = false, length = 64)
    private String wasteCategory;

    /** 拆分前该明细包装总数（取自方案基于的版本快照） */
    @Column(name = "original_quantity", nullable = false, updatable = false)
    private int originalQuantity;

    /** 受影响数量 */
    @Column(name = "affected_quantity", nullable = false, updatable = false)
    private int affectedQuantity;

    /** 未受影响数量：originalQuantity - affectedQuantity，继续沿原联单流转 */
    @Column(name = "unaffected_quantity", nullable = false, updatable = false)
    private int unaffectedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 16)
    private SplitTargetType targetType;

    /** 改道时关联新运输段；泄漏/遗失为空 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id", updatable = false)
    private TransportSegment segment;

    /** 泄漏/遗失时关联损失记录；改道为空 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loss_id", updatable = false)
    private LossRecord loss;

    protected IncidentPackageSplit() {
    }

    public IncidentPackageSplit(IncidentPlan plan, Incident incident, int itemSeq, String wasteCategory,
                                int originalQuantity, int affectedQuantity, int unaffectedQuantity,
                                SplitTargetType targetType, TransportSegment segment, LossRecord loss) {
        this.plan = plan;
        this.incident = incident;
        this.itemSeq = itemSeq;
        this.wasteCategory = wasteCategory;
        this.originalQuantity = originalQuantity;
        this.affectedQuantity = affectedQuantity;
        this.unaffectedQuantity = unaffectedQuantity;
        this.targetType = targetType;
        this.segment = segment;
        this.loss = loss;
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException("包装拆分记录不可修改");
    }

    public Long getId() {
        return id;
    }

    public IncidentPlan getPlan() {
        return plan;
    }

    public Incident getIncident() {
        return incident;
    }

    public int getItemSeq() {
        return itemSeq;
    }

    public String getWasteCategory() {
        return wasteCategory;
    }

    public int getOriginalQuantity() {
        return originalQuantity;
    }

    public int getAffectedQuantity() {
        return affectedQuantity;
    }

    public int getUnaffectedQuantity() {
        return unaffectedQuantity;
    }

    public SplitTargetType getTargetType() {
        return targetType;
    }

    public TransportSegment getSegment() {
        return segment;
    }

    public LossRecord getLoss() {
        return loss;
    }
}
