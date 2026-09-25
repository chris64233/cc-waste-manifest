package com.chris64233.cc.wastemanifest.manifest.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "waste_manifest", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_manifest_no", columnNames = "manifest_no")
})
public class WasteManifest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manifest_no", nullable = false, length = 64)
    private String manifestNo;

    @Column(name = "generator_party", nullable = false, length = 64)
    private String generatorParty;

    @Column(name = "carrier_party", nullable = false, length = 64)
    private String carrierParty;

    @Column(name = "disposer_party", nullable = false, length = 64)
    private String disposerParty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ManifestStatus status;

    @Column(name = "current_custodian", nullable = false, length = 16)
    private String currentCustodian;

    /** 申报总重（各废物明细申报重量之和）。 */
    @Column(name = "declared_total_weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal declaredTotalWeight;

    /** 创建联单时记录的允许差异比例快照，如 0.05 表示 5%。 */
    @Column(name = "weight_tolerance_ratio", nullable = false, precision = 6, scale = 4)
    private BigDecimal weightToleranceRatio;

    @Column(name = "generator_weight", precision = 12, scale = 3)
    private BigDecimal generatorWeight;

    @Column(name = "carrier_weight", precision = 12, scale = 3)
    private BigDecimal carrierWeight;

    @Column(name = "disposer_weight", precision = 12, scale = 3)
    private BigDecimal disposerWeight;

    /** 争议解决后双方一致确认的修正重量。 */
    @Column(name = "resolved_weight", precision = 12, scale = 3)
    private BigDecimal resolvedWeight;

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @Column(name = "last_sequence", nullable = false)
    private int lastSequence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected WasteManifest() {
    }

    public WasteManifest(String manifestNo, String generatorParty, String carrierParty, String disposerParty,
                         BigDecimal declaredTotalWeight, BigDecimal weightToleranceRatio) {
        this.manifestNo = manifestNo;
        this.generatorParty = generatorParty;
        this.carrierParty = carrierParty;
        this.disposerParty = disposerParty;
        this.declaredTotalWeight = declaredTotalWeight;
        this.weightToleranceRatio = weightToleranceRatio;
        this.status = ManifestStatus.CREATED;
        this.currentCustodian = ManifestStatus.CREATED.getCurrentCustodian();
        this.lastSequence = 0;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public int nextSequence() {
        return ++lastSequence;
    }

    public Long getId() {
        return id;
    }

    public String getManifestNo() {
        return manifestNo;
    }

    public String getGeneratorParty() {
        return generatorParty;
    }

    public String getCarrierParty() {
        return carrierParty;
    }

    public String getDisposerParty() {
        return disposerParty;
    }

    public ManifestStatus getStatus() {
        return status;
    }

    public void setStatus(ManifestStatus status) {
        this.status = status;
        this.currentCustodian = status.getCurrentCustodian();
    }

    public String getCurrentCustodian() {
        return currentCustodian;
    }

    public BigDecimal getDeclaredTotalWeight() {
        return declaredTotalWeight;
    }

    public BigDecimal getWeightToleranceRatio() {
        return weightToleranceRatio;
    }

    public BigDecimal getGeneratorWeight() {
        return generatorWeight;
    }

    public void setGeneratorWeight(BigDecimal generatorWeight) {
        this.generatorWeight = generatorWeight;
    }

    public BigDecimal getCarrierWeight() {
        return carrierWeight;
    }

    public void setCarrierWeight(BigDecimal carrierWeight) {
        this.carrierWeight = carrierWeight;
    }

    public BigDecimal getDisposerWeight() {
        return disposerWeight;
    }

    public void setDisposerWeight(BigDecimal disposerWeight) {
        this.disposerWeight = disposerWeight;
    }

    public BigDecimal getResolvedWeight() {
        return resolvedWeight;
    }

    public void setResolvedWeight(BigDecimal resolvedWeight) {
        this.resolvedWeight = resolvedWeight;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }

    public void setLastEventAt(Instant lastEventAt) {
        this.lastEventAt = lastEventAt;
    }

    public int getLastSequence() {
        return lastSequence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
