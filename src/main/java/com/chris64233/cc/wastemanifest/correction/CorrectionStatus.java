package com.chris64233.cc.wastemanifest.correction;

/**
 * 更正申请状态。
 */
public enum CorrectionStatus {
    /** 确认中：等待各涉及方（及监管）决定 */
    PENDING,
    /** 所有必要决定齐备，新版本已生效 */
    EFFECTIVE,
    /** 涉及方拒绝，更正终止 */
    REJECTED,
    /** 参与方撤回，更正终止 */
    WITHDRAWN,
    /** 确认期间出现监管冻结，基于旧版本的更正终止、不得落地 */
    FROZEN,
    /** 确认期间基线版本被其他更正取代，基于旧版本的更正终止、不得落地 */
    SUPERSEDED
}
