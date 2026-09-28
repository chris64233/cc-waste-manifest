package com.chris64233.cc.wastemanifest.manifest;

/**
 * 联单上的活动操作类型：差错更正与运输异常处置共享同一活动守卫，
 * 同一联单同时只能存在一个活动操作（一笔 PENDING 更正或一笔 PENDING 异常处置）。
 */
public enum ActiveOpType {
    CORRECTION,
    INCIDENT
}
