package com.chris64233.cc.wastemanifest.manifest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManifestEventRepository extends JpaRepository<ManifestEvent, Long> {

    Optional<ManifestEvent> findByEventNo(String eventNo);

    List<ManifestEvent> findByManifestOrderByIdAsc(Manifest manifest);

    List<ManifestEvent> findByManifestManifestNoOrderByIdAsc(String manifestNo);
}
