package com.chris64233.cc.wastemanifest.incident;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransportIncidentRepository extends JpaRepository<TransportIncident, Long> {

    Optional<TransportIncident> findByIncidentNo(String incidentNo);

    List<TransportIncident> findByManifestManifestNoOrderByIdAsc(String manifestNo);

    boolean existsByManifestManifestNoAndStatus(String manifestNo, IncidentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from TransportIncident i where i.incidentNo = :incidentNo")
    Optional<TransportIncident> findByIncidentNoForUpdate(@Param("incidentNo") String incidentNo);
}
