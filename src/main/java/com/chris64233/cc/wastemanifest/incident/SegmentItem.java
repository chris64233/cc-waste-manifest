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

import java.math.BigDecimal;

/**
 * 运输段明细：方案生效时一次性固化的受影响包装口径，{@code sourceItemSeq}
 * 指向原联单明细序号，形成包装拆分前后的可追溯关系。
 *
 * <p>未受影响部分不产生段明细（继续沿原联单流转），因此
 * “原明细总量 = 原联单继续流转量 + 全部段明细量（损失段数量可能为 0）”。</p>
 */
@Entity
@Table(name = "transport_segment_items")
public class SegmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "segment_id", nullable = false, updatable = false)
    private TransportSegment segment;

    /** 原联单明细序号 */
    @Column(name = "source_item_seq", nullable = false, updatable = false)
    private int sourceItemSeq;

    @Column(name = "waste_category", nullable = false, updatable = false, length = 64)
    private String wasteCategory;

    @Column(name = "package_count", nullable = false, updatable = false)
    private int packageCount;

    @Column(name = "declared_weight", nullable = false, updatable = false, precision = 19, scale = 3)
    private BigDecimal declaredWeight;

    protected SegmentItem() {
    }

    public SegmentItem(TransportSegment segment, int sourceItemSeq, String wasteCategory,
                       int packageCount, BigDecimal declaredWeight) {
        this.segment = segment;
        this.sourceItemSeq = sourceItemSeq;
        this.wasteCategory = wasteCategory;
        this.packageCount = packageCount;
        this.declaredWeight = declaredWeight;
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException("运输段明细不可修改");
    }

    public Long getId() {
        return id;
    }

    public TransportSegment getSegment() {
        return segment;
    }

    public int getSourceItemSeq() {
        return sourceItemSeq;
    }

    public String getWasteCategory() {
        return wasteCategory;
    }

    public int getPackageCount() {
        return packageCount;
    }

    public BigDecimal getDeclaredWeight() {
        return declaredWeight;
    }
}
