package com.chris64233.cc.wastemanifest.correction;

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

import java.math.BigDecimal;

/**
 * 版本中的废物明细快照，与交接明细一样不可修改。
 */
@Entity
@Table(name = "manifest_version_items")
public class ManifestVersionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false, updatable = false)
    private ManifestVersion version;

    /** 明细在联单中的序号（从 1 开始），更正时据此定位明细 */
    @Column(name = "item_seq", nullable = false, updatable = false)
    private int itemSeq;

    @Column(name = "waste_category", nullable = false, updatable = false, length = 64)
    private String wasteCategory;

    @Column(name = "package_count", nullable = false, updatable = false)
    private int packageCount;

    @Column(name = "declared_weight", nullable = false, updatable = false, precision = 19, scale = 3)
    private BigDecimal declaredWeight;

    protected ManifestVersionItem() {
    }

    public ManifestVersionItem(ManifestVersion version, int itemSeq, String wasteCategory,
                               int packageCount, BigDecimal declaredWeight) {
        this.version = version;
        this.itemSeq = itemSeq;
        this.wasteCategory = wasteCategory;
        this.packageCount = packageCount;
        this.declaredWeight = declaredWeight;
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException("版本明细不可修改");
    }

    public Long getId() {
        return id;
    }

    public ManifestVersion getVersion() {
        return version;
    }

    public int getItemSeq() {
        return itemSeq;
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
