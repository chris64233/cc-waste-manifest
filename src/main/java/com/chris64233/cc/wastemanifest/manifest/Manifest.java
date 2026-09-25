package com.chris64233.cc.wastemanifest.manifest;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "manifests")
public class Manifest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manifest_no", nullable = false, unique = true, updatable = false, length = 64)
    private String manifestNo;

    @Column(name = "generator_id", nullable = false, updatable = false, length = 64)
    private String generatorId;

    @Column(name = "transporter_id", nullable = false, updatable = false, length = 64)
    private String transporterId;

    @Column(name = "disposer_id", nullable = false, updatable = false, length = 64)
    private String disposerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ManifestStatus status = ManifestStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_custodian", nullable = false, length = 32)
    private CustodianRole currentCustodian = CustodianRole.GENERATOR;

    @Column(name = "declared_total_weight", nullable = false, precision = 19, scale = 3)
    private BigDecimal declaredTotalWeight;

    @Column(name = "received_weight", precision = 19, scale = 3)
    private BigDecimal receivedWeight;

    @Column(name = "final_weight", precision = 19, scale = 3)
    private BigDecimal finalWeight;

    @OneToMany(mappedBy = "manifest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ManifestItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Manifest() {
    }

    public Manifest(String manifestNo, String generatorId, String transporterId, String disposerId) {
        this.manifestNo = manifestNo;
        this.generatorId = generatorId;
        this.transporterId = transporterId;
        this.disposerId = disposerId;
    }

    public void addItem(ManifestItem item) {
        items.add(item);
    }

    public void markInTransit() {
        this.status = ManifestStatus.IN_TRANSIT;
        this.currentCustodian = CustodianRole.TRANSPORTER;
    }

    public void markReceivedByTransporter() {
        this.status = ManifestStatus.RECEIVED_BY_TRANSPORTER;
        this.currentCustodian = CustodianRole.TRANSPORTER;
    }

    public void markCompleted(BigDecimal receivedWeight, BigDecimal finalWeight) {
        this.status = ManifestStatus.COMPLETED;
        this.currentCustodian = CustodianRole.DISPOSER;
        this.receivedWeight = receivedWeight;
        this.finalWeight = finalWeight;
    }

    public void markWeightDispute(BigDecimal receivedWeight) {
        this.status = ManifestStatus.WEIGHT_DISPUTE;
        this.currentCustodian = CustodianRole.DISPOSER;
        this.receivedWeight = receivedWeight;
    }

    public Long getId() {
        return id;
    }

    public String getManifestNo() {
        return manifestNo;
    }

    public String getGeneratorId() {
        return generatorId;
    }

    public String getTransporterId() {
        return transporterId;
    }

    public String getDisposerId() {
        return disposerId;
    }

    public ManifestStatus getStatus() {
        return status;
    }

    public CustodianRole getCurrentCustodian() {
        return currentCustodian;
    }

    public BigDecimal getDeclaredTotalWeight() {
        return declaredTotalWeight;
    }

    public void setDeclaredTotalWeight(BigDecimal declaredTotalWeight) {
        this.declaredTotalWeight = declaredTotalWeight;
    }

    public BigDecimal getReceivedWeight() {
        return receivedWeight;
    }

    public BigDecimal getFinalWeight() {
        return finalWeight;
    }

    public List<ManifestItem> getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
