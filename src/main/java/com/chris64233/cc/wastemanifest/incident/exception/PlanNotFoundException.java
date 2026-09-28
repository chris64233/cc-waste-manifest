package com.chris64233.cc.wastemanifest.incident.exception;

public class PlanNotFoundException extends RuntimeException {

    public PlanNotFoundException(String planNo) {
        super("异常处置方案不存在: " + planNo);
    }
}
