package com.chris64233.cc.wastemanifest.manifest.dto;

import java.time.Instant;

public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
}
