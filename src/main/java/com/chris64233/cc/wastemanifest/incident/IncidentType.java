package com.chris64233.cc.wastemanifest.incident;

/**
 * 运输异常类型。
 */
public enum IncidentType {
    /** 泄漏：受影响包装冻结，方案生效后形成损失记录 */
    LEAK,
    /** 遗失：受影响包装冻结，方案生效后形成损失记录 */
    LOSS,
    /** 改道：受影响包装转入新处置方的新运输段 */
    DIVERSION
}
