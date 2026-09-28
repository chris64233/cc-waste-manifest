package com.chris64233.cc.wastemanifest.manifest;

public enum ManifestStatus {
    CREATED,
    IN_TRANSIT,
    RECEIVED_BY_TRANSPORTER,
    WEIGHT_DISPUTE,
    COMPLETED,
    /** 全部包装经异常处置转出（全改道/全损失），原联单无剩余包装继续流转而结案 */
    CLOSED
}
