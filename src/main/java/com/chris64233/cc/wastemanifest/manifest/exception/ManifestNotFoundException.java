package com.chris64233.cc.wastemanifest.manifest.exception;

public class ManifestNotFoundException extends RuntimeException {

    public ManifestNotFoundException(String manifestNo) {
        super("联单不存在: " + manifestNo);
    }
}
