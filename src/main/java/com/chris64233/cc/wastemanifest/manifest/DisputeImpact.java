package com.chris64233.cc.wastemanifest.manifest;

public enum DisputeImpact {
    /** 更正前后均不构成重量争议，原结论不变 */
    CONCLUSION_UNCHANGED,
    /** 更正后申报重量与实收重量偏差回到容差内，原争议本不会发生 */
    DISPUTE_WOULD_NOT_HAVE_OCCURRED,
    /** 更正后申报重量使偏差超出容差，原结论应由直接完成转为争议 */
    DISPUTE_WOULD_HAVE_OCCURRED,
    /** 更正后偏差仍超出容差，原争议结论维持 */
    DISPUTE_WOULD_REMAIN
}
