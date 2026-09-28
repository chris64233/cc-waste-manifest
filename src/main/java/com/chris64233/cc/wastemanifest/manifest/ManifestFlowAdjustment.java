package com.chris64233.cc.wastemanifest.manifest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 联单流向调整：异常方案生效时一次性写入，记录从原联单拆出到运输段/损失记录的
 * 包装数量与重量（按原明细序号）。原联单明细行不可修改，拆减量以此表叠加得出，
 * “原明细量 = 继续沿原联单流转量 + 全部拆出量”。
 */
@Entity
@Table(name = "manifest_flow_adjustments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"incident_no", "item_seq"}))
public class ManifestFlowAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manifest_id", nullable = false, updatable = false)
    private Manifest manifest;

    @Column(name = "incident_no", nullable = false, updatable = false, length = 64)
    private String incidentNo;

    @Column(name = "item_seq", nullable = false, updatable = false)
    private int itemSeq;

    @Column(name = "detached_package_count", nullable = false, updatable = false)
    private int detachedPackageCount;

    @Column(name = "detached_weight", nullable = false, updatable = false, precision = 19, scale = 3)
    private BigDecimal detachedWeight;

    protected ManifestFlowAdjustment() {
    }

    public ManifestFlowAdjustment(Manifest manifest, String incidentNo, int itemSeq,
                                  int detachedPackageCount, BigDecimal detachedWeight) {
        this.manifest = manifest;
        this.incidentNo = incidentNo;
        this.itemSeq = itemSeq;
        this.detachedPackageCount = detachedPackageCount;
        this.detachedWeight = detachedWeight;
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException("流向调整不可修改");
    }

    public Long getId() {
        return id;
    }

    public Manifest getManifest() {
        return manifest;
    }

    public String getIncidentNo() {
        return incidentNo;
    }

    public int getItemSeq() {
        return itemSeq;
    }

    public int getDetachedPackageCount() {
        return detachedPackageCount;
    }

    public BigDecimal getDetachedWeight() {
        return detachedWeight;
    }
}
