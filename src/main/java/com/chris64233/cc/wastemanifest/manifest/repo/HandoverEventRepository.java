package com.chris64233.cc.wastemanifest.manifest.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.wastemanifest.manifest.domain.EventType;
import com.chris64233.cc.wastemanifest.manifest.domain.HandoverEvent;

public interface HandoverEventRepository extends JpaRepository<HandoverEvent, Long> {

    Optional<HandoverEvent> findByEventNo(String eventNo);

    List<HandoverEvent> findByManifestIdOrderBySequenceAsc(Long manifestId);

    Optional<HandoverEvent> findFirstByManifestIdAndEventTypeOrderBySequenceDesc(Long manifestId, EventType eventType);
}
