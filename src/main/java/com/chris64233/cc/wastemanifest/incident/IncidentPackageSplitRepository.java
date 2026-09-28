package com.chris64233.cc.wastemanifest.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentPackageSplitRepository extends JpaRepository<IncidentPackageSplit, Long> {

    List<IncidentPackageSplit> findByPlanIdOrderByIdAsc(Long planId);

    List<IncidentPackageSplit> findByIncidentIdOrderByIdAsc(Long incidentId);

    List<IncidentPackageSplit> findBySegmentIdOrderByIdAsc(Long segmentId);

    List<IncidentPackageSplit> findByLossIdOrderByIdAsc(Long lossId);
}
