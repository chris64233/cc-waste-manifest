package com.chris64233.cc.wastemanifest.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IncidentDecisionRepository extends JpaRepository<IncidentDecision, Long> {

    Optional<IncidentDecision> findByDecisionNo(String decisionNo);
}
