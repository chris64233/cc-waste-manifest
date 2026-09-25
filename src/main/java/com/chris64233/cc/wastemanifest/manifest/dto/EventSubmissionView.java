package com.chris64233.cc.wastemanifest.manifest.dto;

public record EventSubmissionView(boolean replayed, EventView event, ManifestDetailView manifest) {
}
