package com.chris64233.cc.wastemanifest.correction;

/**
 * 更正对原争议结论的影响。
 */
public enum DisputeImpact {
    /** 原争议结论不受影响 */
    NONE,
    /** 原在容差内直接完成，按修正后口径本应进入重量争议 */
    WOULD_HAVE_ENTERED_DISPUTE,
    /** 原进入过重量争议，按修正后口径争议本可避免 */
    DISPUTE_WOULD_HAVE_BEEN_AVOIDED,
    /** 废物类别变化，处置接收依据改变，须监管复核 */
    WASTE_CATEGORY_CHANGED
}
