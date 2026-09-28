package com.chris64233.cc.wastemanifest.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PackageFreezeRepository extends JpaRepository<PackageFreeze, Long> {

    boolean existsByManifestManifestNoAndStatus(String manifestNo, PackageFreezeStatus status);

    List<PackageFreeze> findByIncidentNo(String incidentNo);

    List<PackageFreeze> findByManifestManifestNoAndStatus(String manifestNo, PackageFreezeStatus status);
}
