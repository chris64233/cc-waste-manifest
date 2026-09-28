package com.chris64233.cc.wastemanifest.incident.exception;

public class SegmentNotFoundException extends RuntimeException {

    public SegmentNotFoundException(String segmentNo) {
        super("运输段不存在: " + segmentNo);
    }
}
