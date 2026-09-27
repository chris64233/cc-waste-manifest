package com.chris64233.cc.wastemanifest.manifest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CorrectionDecisionRepository extends JpaRepository<CorrectionDecision, Long> {

    Optional<CorrectionDecision> findByDecisionNo(String decisionNo);

    List<CorrectionDecision> findByCorrectionOrderByIdAsc(ManifestCorrection correction);
}
