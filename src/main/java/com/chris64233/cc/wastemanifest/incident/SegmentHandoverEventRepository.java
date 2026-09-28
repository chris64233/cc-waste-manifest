package com.chris64233.cc.wastemanifest.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SegmentHandoverEventRepository extends JpaRepository<SegmentHandoverEvent, Long> {

    Optional<SegmentHandoverEvent> findByEventNo(String eventNo);

    List<SegmentHandoverEvent> findBySegmentOrderByIdAsc(TransportSegment segment);
}
