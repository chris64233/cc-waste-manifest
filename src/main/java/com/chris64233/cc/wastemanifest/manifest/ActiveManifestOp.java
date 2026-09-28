package com.chris64233.cc.wastemanifest.manifest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 联单活动操作守卫：每联单至多一行（{@code manifestNo} 唯一）。
 *
 * <p>差错更正与运输异常处置互斥——基于同一版本并发时只有一个操作能登记成功，
 * 后来操作必须在该操作结束（生效/拒绝/撤回/冻结/失效）后读取新版本重新计算。
 * 行本身记录被守卫操作登记时所基于的联单版本号，用于结束前的版本复核。</p>
 */
@Entity
@Table(name = "active_manifest_ops",
        uniqueConstraints = @UniqueConstraint(columnNames = "manifest_no"))
public class ActiveManifestOp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manifest_no", nullable = false, updatable = false, length = 64)
    private String manifestNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "op_type", nullable = false, updatable = false, length = 16)
    private ActiveOpType opType;

    /** 活动操作引用（更正号或异常事件号） */
    @Column(name = "ref_no", nullable = false, updatable = false, length = 64)
    private String refNo;

    /** 操作登记时联单的当前有效版本号 */
    @Column(name = "base_version_no", nullable = false, updatable = false)
    private int baseVersionNo;

    protected ActiveManifestOp() {
    }

    public ActiveManifestOp(String manifestNo, ActiveOpType opType, String refNo, int baseVersionNo) {
        this.manifestNo = manifestNo;
        this.opType = opType;
        this.refNo = refNo;
        this.baseVersionNo = baseVersionNo;
    }

    public Long getId() {
        return id;
    }

    public String getManifestNo() {
        return manifestNo;
    }

    public ActiveOpType getOpType() {
        return opType;
    }

    public String getRefNo() {
        return refNo;
    }

    public int getBaseVersionNo() {
        return baseVersionNo;
    }
}
