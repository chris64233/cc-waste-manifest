package com.chris64233.cc.wastemanifest.incident;

/**
 * 运输异常类型。
 */
public enum IncidentType {
    /** 泄漏：受影响包装冻结，处置后形成新运输段 */
    LEAK,
    /** 遗失：受影响包装冻结，处置后形成损失记录 */
    LOSS,
    /** 改道：包装不冻结，必须指定新处置方与预计到达时间 */
    DIVERSION
}
