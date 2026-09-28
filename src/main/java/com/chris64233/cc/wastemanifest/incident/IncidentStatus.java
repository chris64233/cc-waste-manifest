package com.chris64233.cc.wastemanifest.incident;

/**
 * 运输异常处置状态。
 */
public enum IncidentStatus {
    /** 方案确认中：等待产生方、承运方、处置方（改道含新处置方）及必要时监管决定 */
    PENDING,
    /** 所有必要决定齐备，方案一次性生效，包装已拆分 */
    EFFECTIVE,
    /** 任一责任方拒绝，整次处置终止，不留半条运输链 */
    REJECTED,
    /** 承运方（登记方）撤回，处置终止 */
    WITHDRAWN,
    /** 确认期间出现监管冻结，处置终止、不得落地 */
    FROZEN,
    /** 确认期间引用的联单版本已变化（更正生效），处置终止、不得落地 */
    SUPERSEDED
}
