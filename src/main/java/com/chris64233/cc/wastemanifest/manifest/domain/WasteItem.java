package com.chris64233.cc.wastemanifest.manifest.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "waste_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_item_manifest_line", columnNames = {"manifest_id", "line_no"})
})
public class WasteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manifest_id", nullable = false)
    private WasteManifest manifest;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "category", nullable = false, length = 128)
    private String category;

    @Column(name = "package_count", nullable = false)
    private int packageCount;

    @Column(name = "declared_weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal declaredWeight;

    protected WasteItem() {
    }

    public WasteItem(WasteManifest manifest, int lineNo, String category, int packageCount, BigDecimal declaredWeight) {
        this.manifest = manifest;
        this.lineNo = lineNo;
        this.category = category;
        this.packageCount = packageCount;
        this.declaredWeight = declaredWeight;
    }

    public Long getId() {
        return id;
    }

    public WasteManifest getManifest() {
        return manifest;
    }

    public int getLineNo() {
        return lineNo;
    }

    public String getCategory() {
        return category;
    }

    public int getPackageCount() {
        return packageCount;
    }

    public BigDecimal getDeclaredWeight() {
        return declaredWeight;
    }
}
