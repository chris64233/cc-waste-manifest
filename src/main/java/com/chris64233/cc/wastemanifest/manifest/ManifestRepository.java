package com.chris64233.cc.wastemanifest.manifest;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ManifestRepository extends JpaRepository<Manifest, Long> {

    boolean existsByManifestNo(String manifestNo);

    Optional<Manifest> findByManifestNo(String manifestNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Manifest m where m.manifestNo = :manifestNo")
    Optional<Manifest> findByManifestNoForUpdate(@Param("manifestNo") String manifestNo);
}
