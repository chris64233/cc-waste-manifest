package com.chris64233.cc.wastemanifest.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IncidentPlanDecisionRepository extends JpaRepository<IncidentPlanDecision, Long> {

    Optional<IncidentPlanDecision> findByDecisionNo(String decisionNo);
}
