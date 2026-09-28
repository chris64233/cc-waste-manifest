package com.chris64233.cc.wastemanifest.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByEventNo(String eventNo);

    List<Incident> findByManifestManifestNoOrderByIdAsc(String manifestNo);

    /** 是否存在未处置完成（OPEN）的异常；用于交接冻结与并发守卫。 */
    boolean existsByManifestManifestNoAndStatus(String manifestNo, IncidentStatus status);
}
