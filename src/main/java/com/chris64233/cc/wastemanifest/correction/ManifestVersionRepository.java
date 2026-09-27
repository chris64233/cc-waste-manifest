package com.chris64233.cc.wastemanifest.correction;

import com.chris64233.cc.wastemanifest.manifest.Manifest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ManifestVersionRepository extends JpaRepository<ManifestVersion, Long> {

    @Query("select v from ManifestVersion v where v.manifest.manifestNo = :manifestNo "
            + "and v.versionNo = :versionNo")
    Optional<ManifestVersion> findByManifestNoAndVersionNo(@Param("manifestNo") String manifestNo,
                                                           @Param("versionNo") int versionNo);

    @Query("select v from ManifestVersion v where v.manifest = :manifest and v.status = EFFECTIVE")
    Optional<ManifestVersion> findEffectiveByManifest(@Param("manifest") Manifest manifest);

    @Query("select v from ManifestVersion v where v.manifest.manifestNo = :manifestNo order by v.versionNo asc")
    List<ManifestVersion> findByManifestNoOrderByVersionNoAsc(@Param("manifestNo") String manifestNo);

    /** 取版本号最大值，用于确定下一个版本号；加行锁保证版本号分配串行。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ManifestVersion v where v.manifest = :manifest order by v.versionNo desc")
    List<ManifestVersion> findByManifestForUpdate(@Param("manifest") Manifest manifest);
}
