package com.chris64233.cc.wastemanifest.incident.exception;

public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(String eventNo) {
        super("运输异常不存在: " + eventNo);
    }
}
