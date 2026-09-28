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
 * 异常受影响包装：按联单明细序号定位，记录受影响包装数量与登记时的废物类别快照。
 * 登记后即冻结，在处置方案生效前不得继续正常交接。
 */
@Entity
@Table(name = "incident_affected_packages")
public class IncidentAffectedPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, updatable = false)
    private Incident incident;

    @Column(name = "item_seq", nullable = false, updatable = false)
    private int itemSeq;

    @Column(name = "waste_category", nullable = false, updatable = false, length = 64)
    private String wasteCategory;

    @Column(name = "affected_quantity", nullable = false, updatable = false)
    private int affectedQuantity;

    protected IncidentAffectedPackage() {
    }

    public IncidentAffectedPackage(Incident incident, int itemSeq, String wasteCategory,
                                   int affectedQuantity) {
        this.incident = incident;
        this.itemSeq = itemSeq;
        this.wasteCategory = wasteCategory;
        this.affectedQuantity = affectedQuantity;
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException("异常受影响包装不可修改");
    }

    public Long getId() {
        return id;
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

    public int getAffectedQuantity() {
        return affectedQuantity;
    }
}
