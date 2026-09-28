package com.chris64233.cc.wastemanifest.manifest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActiveManifestOpRepository extends JpaRepository<ActiveManifestOp, Long> {

    Optional<ActiveManifestOp> findByManifestNo(String manifestNo);

    void deleteByManifestNo(String manifestNo);
}
