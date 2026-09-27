package com.chris64233.cc.wastemanifest.manifest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManifestCorrectionRepository extends JpaRepository<ManifestCorrection, Long> {

    Optional<ManifestCorrection> findByCorrectionNo(String correctionNo);

    boolean existsByManifestAndStatus(Manifest manifest, CorrectionStatus status);

    List<ManifestCorrection> findByManifestAndStatus(Manifest manifest, CorrectionStatus status);

    List<ManifestCorrection> findByManifestManifestNoOrderByIdAsc(String manifestNo);
}
