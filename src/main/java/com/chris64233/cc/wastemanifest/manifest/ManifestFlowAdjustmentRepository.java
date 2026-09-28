package com.chris64233.cc.wastemanifest.manifest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ManifestFlowAdjustmentRepository extends JpaRepository<ManifestFlowAdjustment, Long> {

    List<ManifestFlowAdjustment> findByManifestOrderByItemSeqAsc(Manifest manifest);

    List<ManifestFlowAdjustment> findByManifestManifestNo(String manifestNo);
}
