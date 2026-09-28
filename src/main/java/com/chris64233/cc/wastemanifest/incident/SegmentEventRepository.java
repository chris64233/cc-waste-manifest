package com.chris64233.cc.wastemanifest.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SegmentEventRepository extends JpaRepository<SegmentEvent, Long> {

    Optional<SegmentEvent> findByEventNo(String eventNo);

    List<SegmentEvent> findBySegmentIdOrderByIdAsc(Long segmentId);
}
