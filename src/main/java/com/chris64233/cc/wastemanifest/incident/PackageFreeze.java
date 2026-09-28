package com.chris64233.cc.wastemanifest.incident;

import com.chris64233.cc.wastemanifest.manifest.Manifest;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 包装冻结记录。泄漏或遗失登记成功后，受影响包装立即冻结，
 * 冻结期间原联单不能继续正常交接；方案生效（包装拆出）或处置终止时释放。
 */
@Entity
@Table(name = "package_freezes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"incident_no", "item_seq"}))
public class PackageFreeze {

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

    @Column(name = "package_count", nullable = false, updatable = false)
    private int packageCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PackageFreezeStatus status = PackageFreezeStatus.FROZEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "released_at")
    private Instant releasedAt;

    protected PackageFreeze() {
    }

    public PackageFreeze(Manifest manifest, String incidentNo, int itemSeq, int packageCount) {
        this.manifest = manifest;
        this.incidentNo = incidentNo;
        this.itemSeq = itemSeq;
        this.packageCount = packageCount;
    }

    public void release() {
        if (status == PackageFreezeStatus.FROZEN) {
            this.status = PackageFreezeStatus.RELEASED;
            this.releasedAt = Instant.now();
        }
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

    public int getPackageCount() {
        return packageCount;
    }

    public PackageFreezeStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }
}
