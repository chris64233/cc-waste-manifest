package com.chris64233.cc.wastemanifest.incident;

/**
 * 运输段状态。新运输段沿 运输中 → 已交付 → 已接收 推进；损失记录恒为损失结案。
 */
public enum SegmentStatus {
    /** 新运输段：承运方运输中 */
    IN_TRANSIT,
    /** 承运方已交付目的地，等待处置方接收 */
    DELIVERED,
    /** 处置方已接收，段完成 */
    COMPLETED,
    /** 损失记录：包装已损失，不进入交接 */
    LOST
}
