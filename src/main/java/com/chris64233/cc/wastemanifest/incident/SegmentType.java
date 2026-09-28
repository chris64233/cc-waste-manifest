package com.chris64233.cc.wastemanifest.incident;

/**
 * 运输段类型：异常方案生效后，受影响包装形成的去向。
 */
public enum SegmentType {
    /** 新运输段（泄漏后重新包装继续运输，或改道至新处置方） */
    NEW_TRANSPORT,
    /** 损失记录（遗失，包装退出运输链） */
    LOSS
}
