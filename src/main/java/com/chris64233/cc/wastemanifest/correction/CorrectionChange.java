package com.chris64233.cc.wastemanifest.correction;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 更正申请中的单项变更：保存字段、明细序号、原始值与新值。
 */
@Entity
@Table(name = "manifest_correction_changes")
public class CorrectionChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "correction_id", nullable = false, updatable = false)
    private Correction correction;

    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false, updatable = false, length = 32)
    private CorrectionField field;

    /** PACKAGE_COUNT / WEIGHT / WASTE_CATEGORY 均针对明细行，从 1 开始 */
    @Column(name = "item_seq", nullable = false, updatable = false)
    private int itemSeq;

    @Column(name = "old_value", nullable = false, updatable = false, length = 64)
    private String oldValue;

    @Column(name = "new_value", nullable = false, updatable = false, length = 64)
    private String newValue;

    protected CorrectionChange() {
    }

    public CorrectionChange(Correction correction, CorrectionField field, int itemSeq,
                            String oldValue, String newValue) {
        this.correction = correction;
        this.field = field;
        this.itemSeq = itemSeq;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException("更正变更项不可修改");
    }

    public Long getId() {
        return id;
    }

    public Correction getCorrection() {
        return correction;
    }

    public CorrectionField getField() {
        return field;
    }

    public int getItemSeq() {
        return itemSeq;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public BigDecimal oldDecimal() {
        return new BigDecimal(oldValue);
    }

    public BigDecimal newDecimal() {
        return new BigDecimal(newValue);
    }
}
