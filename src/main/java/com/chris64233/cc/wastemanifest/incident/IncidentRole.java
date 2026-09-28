package com.chris64233.cc.wastemanifest.incident;

/**
 * 异常处置确认角色。改道时原处置方（OLD_DISPOSER）与新处置方（NEW_DISPOSER）
 * 是两个独立确认责任；监管方在涉及废物类别或数量变化时作出监管决定。
 */
public enum IncidentRole {
    GENERATOR,
    TRANSPORTER,
    OLD_DISPOSER,
    NEW_DISPOSER,
    REGULATOR
}
