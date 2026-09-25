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
@Table(name = "manifest_items")
public class ManifestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manifest_id", nullable = false, updatable = false)
    private Manifest manifest;

    @Column(name = "waste_category", nullable = false, updatable = false, length = 64)
    private String wasteCategory;

    @Column(name = "package_count", nullable = false, updatable = false)
    private int packageCount;

    @Column(name = "declared_weight", nullable = false, updatable = false, precision = 19, scale = 3)
    private BigDecimal declaredWeight;

    protected ManifestItem() {
    }

    public ManifestItem(Manifest manifest, String wasteCategory, int packageCount, BigDecimal declaredWeight) {
        this.manifest = manifest;
        this.wasteCategory = wasteCategory;
        this.packageCount = packageCount;
        this.declaredWeight = declaredWeight;
    }

    public Long getId() {
        return id;
    }

    public Manifest getManifest() {
        return manifest;
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
