package com.chris64233.cc.wastemanifest.manifest.repo;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.cc.wastemanifest.manifest.domain.WasteManifest;

public interface WasteManifestRepository extends JpaRepository<WasteManifest, Long> {

    Optional<WasteManifest> findByManifestNo(String manifestNo);

    boolean existsByManifestNo(String manifestNo);

    /** 只取主键，避免把未加锁实体装入持久化上下文。 */
    @Query("select m.id from WasteManifest m where m.manifestNo = :manifestNo")
    Optional<Long> findIdByManifestNo(@Param("manifestNo") String manifestNo);

    /** 悲观写锁：串行化同一联单上的交接 / 争议确认，防止并发跳过状态。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from WasteManifest m where m.id = :id")
    Optional<WasteManifest> findByIdForUpdate(@Param("id") Long id);
}
