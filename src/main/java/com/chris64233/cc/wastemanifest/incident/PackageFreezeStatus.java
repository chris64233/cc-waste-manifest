package com.chris64233.cc.wastemanifest.incident;

public enum PackageFreezeStatus {
    /** 因泄漏/遗失登记而冻结，正常交接暂停 */
    FROZEN,
    /** 方案生效（包装已拆出）或处置终止（拒绝/撤回/冻结/失效）后释放 */
    RELEASED
}
