package com.chris64233.cc.wastemanifest.manifest.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.wastemanifest.manifest.domain.WasteItem;

public interface WasteItemRepository extends JpaRepository<WasteItem, Long> {

    List<WasteItem> findByManifestIdOrderByLineNoAsc(Long manifestId);
}
