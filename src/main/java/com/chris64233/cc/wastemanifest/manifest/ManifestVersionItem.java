package com.chris64233.cc.wastemanifest.manifest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "manifest_version_items")
public class ManifestVersionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false, updatable = false)
    private ManifestVersion version;

    @Column(name = "item_index", nullable = false, updatable = false)
    private int itemIndex;

    @Column(name = "waste_category", nullable = false, updatable = false, length = 64)
    private String wasteCategory;

    @Column(name = "package_count", nullable = false, updatable = false)
    private int packageCount;

    @Column(name = "declared_weight", nullable = false, updatable = false, precision = 19, scale = 3)
    private BigDecimal declaredWeight;

    protected ManifestVersionItem() {
    }

    public ManifestVersionItem(ManifestVersion version, int itemIndex, String wasteCategory,
                               int packageCount, BigDecimal declaredWeight) {
        this.version = version;
        this.itemIndex = itemIndex;
        this.wasteCategory = wasteCategory;
        this.packageCount = packageCount;
        this.declaredWeight = declaredWeight;
    }

    public Long getId() {
        return id;
    }

    public ManifestVersion getVersion() {
        return version;
    }

    public int getItemIndex() {
        return itemIndex;
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
