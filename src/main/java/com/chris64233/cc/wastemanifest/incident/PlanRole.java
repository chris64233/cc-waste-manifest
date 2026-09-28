package com.chris64233.cc.wastemanifest.incident;

/**
 * 异常处置方案的确认责任方。
 */
public enum PlanRole {
    /** 产生方 */
    GENERATOR,
    /** 承运方（方案提出方） */
    TRANSPORTER,
    /** 原处置方 */
    OLD_DISPOSER,
    /** 改道时的新处置方 */
    NEW_DISPOSER,
    /** 监管方（涉及废物类别或数量变化时决定） */
    REGULATOR
}
