package com.chris64233.cc.wastemanifest.manifest.exception;

public class CorrectionNotFoundException extends RuntimeException {

    public CorrectionNotFoundException(String correctionNo) {
        super("更正不存在: " + correctionNo);
    }
}
