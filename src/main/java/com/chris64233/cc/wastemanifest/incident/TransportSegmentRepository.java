package com.chris64233.cc.wastemanifest.incident;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransportSegmentRepository extends JpaRepository<TransportSegment, Long> {

    Optional<TransportSegment> findBySegmentNo(String segmentNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TransportSegment s where s.segmentNo = :segmentNo")
    Optional<TransportSegment> findBySegmentNoForUpdate(@Param("segmentNo") String segmentNo);

    List<TransportSegment> findByParentManifestManifestNoOrderBySeqNoAsc(String manifestNo);

    List<TransportSegment> findByIncidentIdOrderBySeqNoAsc(Long incidentId);
}
