package com.lnmiit.docreq.repository;

import com.lnmiit.docreq.model.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    List<StoredFile> findByRequestIdOrderByUploadedAtAsc(Long requestId);

    Optional<StoredFile> findFirstByRequestIdAndKindOrderByUploadedAtDesc(Long requestId, StoredFile.Kind kind);
}
