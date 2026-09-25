package com.chris64233.cc.wastemanifest.manifest.domain;

/**
 * 联单生命周期状态。currentCustodian 表示该状态下废物的当前保管方角色。
 */
public enum ManifestStatus {

    CREATED("GENERATOR", "已创建，废物在产生方"),
    IN_TRANSIT("CARRIER", "承运方已接货，运输中"),
    DISPUTED("DISPOSER", "重量争议，等待两方确认"),
    COMPLETED("DISPOSER", "联单完成");

    private final String currentCustodian;
    private final String description;

    ManifestStatus(String currentCustodian, String description) {
        this.currentCustodian = currentCustodian;
        this.description = description;
    }

    public String getCurrentCustodian() {
        return currentCustodian;
    }

    public String getDescription() {
        return description;
    }
}
