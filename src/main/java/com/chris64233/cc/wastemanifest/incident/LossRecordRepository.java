package com.chris64233.cc.wastemanifest.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LossRecordRepository extends JpaRepository<LossRecord, Long> {

    Optional<LossRecord> findByLossNo(String lossNo);

    List<LossRecord> findByManifestManifestNoOrderByIdAsc(String manifestNo);
}
