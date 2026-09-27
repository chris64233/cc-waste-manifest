package com.chris64233.cc.wastemanifest.manifest;

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

@Entity
@Table(name = "correction_changes")
public class CorrectionChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "correction_id", nullable = false, updatable = false)
    private ManifestCorrection correction;

    @Column(name = "item_index", nullable = false, updatable = false)
    private int itemIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private CorrectionField field;

    @Column(name = "old_value", nullable = false, updatable = false, length = 64)
    private String oldValue;

    @Column(name = "new_value", nullable = false, updatable = false, length = 64)
    private String newValue;

    protected CorrectionChange() {
    }

    public CorrectionChange(ManifestCorrection correction, int itemIndex, CorrectionField field,
                            String oldValue, String newValue) {
        this.correction = correction;
        this.itemIndex = itemIndex;
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public Long getId() {
        return id;
    }

    public int getItemIndex() {
        return itemIndex;
    }

    public CorrectionField getField() {
        return field;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }
}
