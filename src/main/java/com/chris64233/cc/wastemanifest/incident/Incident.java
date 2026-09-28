package com.chris64233.cc.wastemanifest.incident;

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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 运输异常登记。承运方在运输途中（联单处于承运方保管期间）登记泄漏、遗失或改道。
 *
 * <p>登记即冻结受影响包装：在处置方案生效前，相关包装不能继续正常交接。
 * 异常只能引用登记时的当前有效版本（{@code baseVersionNo}）；登记事件号全局唯一，
 * 同号同内容幂等、同号异内容冲突。</p>
 */
@Entity
@Table(name = "transport_incidents",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "event_no"),
                // 活动异常守卫：一张联单同时只能有一笔未处置完成的异常，终态置空
                @UniqueConstraint(columnNames = "active_manifest_no")
        })
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_no", nullable = false, updatable = false, length = 64)
    private String eventNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manifest_id", nullable = false, updatable = false)
    private Manifest manifest;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false, updatable = false, length = 16)
    private IncidentType type;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "location", nullable = false, updatable = false, length = 200)
    private String location;

    @Column(name = "evidence_ref", nullable = false, updatable = false, length = 500)
    private String evidenceRef;

    /** 登记时联单的当前有效版本号（在途联单为 1） */
    @Column(name = "base_version_no", nullable = false, updatable = false)
    private int baseVersionNo;

    /** OPEN：已登记、处置方案未生效（受影响包装冻结中）；RESOLVED：方案已一次性生效 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentStatus status = IncidentStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** OPEN 期间等于联单号（数据库唯一约束保证一联单至多一笔活动异常），终态置空 */
    @Column(name = "active_manifest_no", length = 64)
    private String activeManifestNo;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<IncidentAffectedPackage> affectedPackages = new ArrayList<>();

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<IncidentPlan> plans = new ArrayList<>();

    protected Incident() {
    }

    public Incident(String eventNo, Manifest manifest, IncidentType type, Instant occurredAt,
                    String location, String evidenceRef, int baseVersionNo) {
        this.eventNo = eventNo;
        this.manifest = manifest;
        this.type = type;
        this.occurredAt = occurredAt;
        this.location = location;
        this.evidenceRef = evidenceRef;
        this.baseVersionNo = baseVersionNo;
        this.activeManifestNo = manifest.getManifestNo();
    }

    public void addAffectedPackage(IncidentAffectedPackage affectedPackage) {
        affectedPackages.add(affectedPackage);
    }

    public void addPlan(IncidentPlan plan) {
        plans.add(plan);
    }

    public boolean isOpen() {
        return status == IncidentStatus.OPEN;
    }

    public void markResolved() {
        this.status = IncidentStatus.RESOLVED;
        this.resolvedAt = Instant.now();
        this.activeManifestNo = null;
    }

    public Long getId() {
        return id;
    }

    public String getEventNo() {
        return eventNo;
    }

    public Manifest getManifest() {
        return manifest;
    }

    public IncidentType getType() {
        return type;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getLocation() {
        return location;
    }

    public String getEvidenceRef() {
        return evidenceRef;
    }

    public int getBaseVersionNo() {
        return baseVersionNo;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public List<IncidentAffectedPackage> getAffectedPackages() {
        return affectedPackages;
    }

    public List<IncidentPlan> getPlans() {
        return plans;
    }
}
