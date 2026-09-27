package com.chris64233.cc.wastemanifest.correction;

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

import com.chris64233.cc.wastemanifest.manifest.Manifest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 联单的不可变版本快照。版本 1 为联单完成时的原始版本；更正生效后追加新版本，
 * 旧版本置为 SUPERSEDED，任何版本内容都不再修改。
 */
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
    @Column(nullable = false, length = 32)
    private VersionStatus status = VersionStatus.EFFECTIVE;

    @Column(name = "declared_total_weight", nullable = false, updatable = false, precision = 19, scale = 3)
    private BigDecimal declaredTotalWeight;

    @Column(name = "received_weight", updatable = false, precision = 19, scale = 3)
    private BigDecimal receivedWeight;

    @Column(name = "final_weight", updatable = false, precision = 19, scale = 3)
    private BigDecimal finalWeight;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("itemSeq ASC")
    private List<ManifestVersionItem> items = new ArrayList<>();

    @Column(name = "effective_at", nullable = false, updatable = false)
    private Instant effectiveAt = Instant.now();

    protected ManifestVersion() {
    }

    public ManifestVersion(Manifest manifest, int versionNo, BigDecimal declaredTotalWeight,
                           BigDecimal receivedWeight, BigDecimal finalWeight) {
        this.manifest = manifest;
        this.versionNo = versionNo;
        this.declaredTotalWeight = declaredTotalWeight;
        this.receivedWeight = receivedWeight;
        this.finalWeight = finalWeight;
    }

    public void addItem(ManifestVersionItem item) {
        items.add(item);
    }

    public void supersede() {
        this.status = VersionStatus.SUPERSEDED;
    }

    public boolean isEffective() {
        return status == VersionStatus.EFFECTIVE;
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

    public VersionStatus getStatus() {
        return status;
    }

    public BigDecimal getDeclaredTotalWeight() {
        return declaredTotalWeight;
    }

    public BigDecimal getReceivedWeight() {
        return receivedWeight;
    }

    public BigDecimal getFinalWeight() {
        return finalWeight;
    }

    public List<ManifestVersionItem> getItems() {
        return items;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }
}
