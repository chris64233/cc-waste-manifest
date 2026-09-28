package com.chris64233.cc.wastemanifest.incident;

/**
 * 异常处置方案状态。
 */
public enum PlanStatus {
    /** 确认中：等待产生方、承运方、（新旧）处置方及监管按责任确认 */
    PENDING,
    /** 所有必要确认齐备，包装拆分与新运输段/损失记录已一次性生成 */
    EFFECTIVE,
    /** 责任方拒绝，方案终止 */
    REJECTED,
    /** 承运方撤回，方案终止 */
    WITHDRAWN,
    /** 确认期间出现监管冻结，方案终止、不得留下半条运输链 */
    FROZEN,
    /** 确认期间联单版本被其他操作推进，基于旧版本的方案终止、不得落地 */
    SUPERSEDED
}
