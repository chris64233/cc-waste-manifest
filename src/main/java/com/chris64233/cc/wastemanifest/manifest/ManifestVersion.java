package com.chris64233.cc.wastemanifest.manifest;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "manifest_versions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"manifest_id", "version_no"}))
public class ManifestVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manifest_id", nullable = false, updatable = false)
    private Manifest manifest;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private VersionSource source;

    @Column(name = "correction_no", updatable = false, length = 64)
    private String correctionNo;

    @Column(name = "declared_total_weight", nullable = false, precision = 19, scale = 3)
    private BigDecimal declaredTotalWeight;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("itemIndex ASC")
    private List<ManifestVersionItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ManifestVersion() {
    }

    public ManifestVersion(Manifest manifest, int versionNo, VersionSource source, String correctionNo) {
        this.manifest = manifest;
        this.versionNo = versionNo;
        this.source = source;
        this.correctionNo = correctionNo;
    }

    public void addItem(ManifestVersionItem item) {
        items.add(item);
    }

    public Long getId() {
        return id;
    }

    public Manifest getManifest() {
        return manifest;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public VersionSource getSource() {
        return source;
    }

    public String getCorrectionNo() {
        return correctionNo;
    }

    public BigDecimal getDeclaredTotalWeight() {
        return declaredTotalWeight;
    }

    public void setDeclaredTotalWeight(BigDecimal declaredTotalWeight) {
        this.declaredTotalWeight = declaredTotalWeight;
    }

    public List<ManifestVersionItem> getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
