package com.chris64233.cc.wastemanifest.manifest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManifestVersionRepository extends JpaRepository<ManifestVersion, Long> {

    Optional<ManifestVersion> findByManifestAndVersionNo(Manifest manifest, int versionNo);

    List<ManifestVersion> findByManifestManifestNoOrderByVersionNoAsc(String manifestNo);
}
