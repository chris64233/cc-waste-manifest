package com.chris64233.cc.wastemanifest.correction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CorrectionRepository extends JpaRepository<Correction, Long> {

    boolean existsByCorrectionNo(String correctionNo);

    Optional<Correction> findByCorrectionNo(String correctionNo);

    /** 同一联单同时只能有一笔活动（PENDING）更正。 */
    boolean existsByManifestManifestNoAndStatus(String manifestNo, CorrectionStatus status);

    List<Correction> findByManifestManifestNoOrderByIdAsc(String manifestNo);
}
