package com.lnmiit.docreq.repository;

import com.lnmiit.docreq.model.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {

    List<DocumentType> findByActiveTrueOrderByNameAsc();

    List<DocumentType> findAllByOrderByCodeAsc();

    Optional<DocumentType> findByCode(String code);

    boolean existsByCode(String code);
}
