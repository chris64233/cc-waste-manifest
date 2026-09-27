package com.chris64233.cc.wastemanifest.correction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CorrectionDecisionRepository extends JpaRepository<CorrectionDecision, Long> {

    Optional<CorrectionDecision> findByDecisionNo(String decisionNo);
}
