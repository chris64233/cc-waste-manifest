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
 * 异常受影响包装行：按联单明细序号定位，记录受影响包装数量与重量。
 *
 * <p>泄漏/遗失登记后这些包装立即冻结；方案生效时一次性拆分到运输段或损失记录。
 * {@code result*} 为方案处置后的口径（如泄漏后重新包装、回收重量变化、废物类别变化），
 * 不填表示与受影响原始口径一致；损失记录结果数量为 0。</p>
 */
@Entity
@Table(name = "incident_affected_lines")
public class IncidentAffectedLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, updatable = false)
    private TransportIncident incident;

    @Column(name = "item_seq", nullable = false, updatable = false)
    private int itemSeq;

    @Column(name = "waste_category", nullable = false, updatable = false, length = 64)
    private String wasteCategory;

    @Column(name = "affected_package_count", nullable = false, updatable = false)
    private int affectedPackageCount;

    @Column(name = "affected_weight", nullable = false, updatable = false, precision = 19, scale = 3)
    private BigDecimal affectedWeight;

    @Column(name = "result_package_count", updatable = false)
    private Integer resultPackageCount;

    @Column(name = "result_weight", updatable = false, precision = 19, scale = 3)
    private BigDecimal resultWeight;

    @Column(name = "result_waste_category", updatable = false, length = 64)
    private String resultWasteCategory;

    protected IncidentAffectedLine() {
    }

    public IncidentAffectedLine(TransportIncident incident, int itemSeq, String wasteCategory,
                                int affectedPackageCount, BigDecimal affectedWeight,
                                Integer resultPackageCount, BigDecimal resultWeight,
                                String resultWasteCategory) {
        this.incident = incident;
        this.itemSeq = itemSeq;
        this.wasteCategory = wasteCategory;
        this.affectedPackageCount = affectedPackageCount;
        this.affectedWeight = affectedWeight;
        this.resultPackageCount = resultPackageCount;
        this.resultWeight = resultWeight;
        this.resultWasteCategory = resultWasteCategory;
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException("受影响包装行不可修改");
    }

    public Long getId() {
        return id;
    }

    public TransportIncident getIncident() {
        return incident;
    }

    public int getItemSeq() {
        return itemSeq;
    }

    public String getWasteCategory() {
        return wasteCategory;
    }

    public int getAffectedPackageCount() {
        return affectedPackageCount;
    }

    public BigDecimal getAffectedWeight() {
        return affectedWeight;
    }

    public Integer getResultPackageCount() {
        return resultPackageCount;
    }

    public BigDecimal getResultWeight() {
        return resultWeight;
    }

    public String getResultWasteCategory() {
        return resultWasteCategory;
    }
}
